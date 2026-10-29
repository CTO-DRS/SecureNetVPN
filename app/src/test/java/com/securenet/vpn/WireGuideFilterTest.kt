package com.securenet.vpn

import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideDifficulty
import com.securenet.vpn.guide.InMemoryGuideStore
import com.securenet.vpn.guide.WireGuideCatalog
import com.securenet.vpn.guide.WireGuideFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8 — search/filter logic for the config-sources guide. The catalog is
 * static shipped content; a broken filter (e.g. one that hides everything,
 * or ignores Arabic tokens) would silently degrade the feature.
 */
class WireGuideFilterTest {

    private val entries = WireGuideCatalog.entries

    @Test
    fun `blank query matches every entry`() {
        assertEquals(entries.size, WireGuideFilter.filter(entries, "   ").size)
    }

    @Test
    fun `name tokens match the right entry`() {
        val hits = WireGuideFilter.filter(entries, "mullvad")
        assertEquals(listOf("mullvad"), hits.map { it.id })
    }

    @Test
    fun `english keyword matches aliases`() {
        // "warp" is a curated keyword of the Cloudflare entry
        val hits = WireGuideFilter.filter(entries, "warp")
        assertTrue(hits.map { it.id }.contains("cloudflare-warp-wgcf"))
        assertTrue(hits.all { it.category == GuideCategory.FREE })
    }

    @Test
    fun `arabic keyword matches — كلاودفلير`() {
        val hits = WireGuideFilter.filter(entries, "كلاودفلير")
        assertEquals(listOf("cloudflare-warp-wgcf"), hits.map { it.id })
    }

    @Test
    fun `arabic category terms select whole categories`() {
        // v1.8.12 — price terms stay whole-category selectors for FREE/PAID
        // and now ALSO reach the self-hosted / router entries that carry an
        // explicit price marker in their curated keywords, so "مجاني" and
        // "مدفوع" span sections by design.
        // v1.8.13 — the five mesh tools join the free-marked set: they are
        // free software / free personal or community tiers.
        val free = WireGuideFilter.filter(entries, "مجاني")
        val paid = WireGuideFilter.filter(entries, "مدفوع")
        assertTrue(free.isNotEmpty())
        assertTrue(paid.isNotEmpty())
        // every hit is either a native FREE/PAID entry or one of the
        // v1.8.12/v1.8.13 entries that deliberately carry the price marker
        val freeMarked = setOf(
            "wgdashboard", "ansible-wireguard", "oracle-alwaysfree", "openbsd-wg",
            "headscale", "tailscale", "netbird", "netmaker", "firezone"
        )
        val paidMarked = setOf(
            "hetzner-cloud", "firewalla", "netgate-appliance", "keenetic", "turris-omnia"
        )
        assertTrue(free.all { it.category == GuideCategory.FREE || it.id in freeMarked })
        assertTrue(paid.all { it.category == GuideCategory.PAID || it.id in paidMarked })
        // exact, deterministic reach: the whole native section + marked entries
        assertEquals(
            WireGuideCatalog.byCategory(GuideCategory.FREE).map { it.id }.toSet() + freeMarked,
            free.map { it.id }.toSet()
        )
        assertEquals(
            WireGuideCatalog.byCategory(GuideCategory.PAID).map { it.id }.toSet() + paidMarked,
            paid.map { it.id }.toSet()
        )
        // v1.8.12 — the price terms now cross sections
        assertTrue(free.any { it.category == GuideCategory.SELF_HOSTED })
        assertTrue(free.any { it.category == GuideCategory.ROUTERS })
        assertTrue(paid.any { it.category == GuideCategory.SELF_HOSTED })
        assertTrue(paid.any { it.category == GuideCategory.ROUTERS })
        // v1.8.13 — and they reach the new mesh section too
        assertTrue(free.any { it.category == GuideCategory.MESH })
    }

    @Test
    fun `mesh category terms select the whole mesh section`() {
        val expected = WireGuideCatalog.byCategory(GuideCategory.MESH).map { it.id }.toSet()
        assertEquals(
            setOf("headscale", "tailscale", "netbird", "netmaker", "firezone"),
            expected
        )
        // the EN + AR section words all reach exactly the mesh section
        listOf("mesh", "شبكات", "ميش").forEach { term ->
            assertEquals(
                "term '$term' must select the mesh section",
                expected,
                WireGuideFilter.filter(entries, term).map { it.id }.toSet()
            )
        }
        // and the old self-hosted section no longer contains the mesh tools
        assertTrue(
            WireGuideCatalog.byCategory(GuideCategory.SELF_HOSTED)
                .none { it.id in expected }
        )
    }

    // ---------------- v1.8.13 — quick price filter ----------------

    @Test
    fun `priceOf derives free and paid from categories and markers`() {
        // native sections are self-describing
        WireGuideCatalog.byCategory(GuideCategory.FREE).forEach {
            assertEquals(WireGuideFilter.Price.FREE, WireGuideFilter.priceOf(it))
        }
        WireGuideCatalog.byCategory(GuideCategory.PAID).forEach {
            assertEquals(WireGuideFilter.Price.PAID, WireGuideFilter.priceOf(it))
        }
        // cross-section markers (v1.8.12 + the v1.8.13 mesh tools)
        listOf("wgdashboard", "openbsd-wg", "tailscale", "firezone").forEach { id ->
            assertEquals(
                "$id must classify as FREE",
                WireGuideFilter.Price.FREE,
                WireGuideFilter.priceOf(WireGuideCatalog.byId(id)!!)
            )
        }
        listOf("hetzner-cloud", "firewalla").forEach { id ->
            assertEquals(
                "$id must classify as PAID",
                WireGuideFilter.Price.PAID,
                WireGuideFilter.priceOf(WireGuideCatalog.byId(id)!!)
            )
        }
        // a neutral app with no price signal stays unclassified
        assertEquals(null, WireGuideFilter.priceOf(WireGuideCatalog.byId("wg-android")!!))
    }

    @Test
    fun `quick price filter keeps one class and All keeps everything`() {
        val free = WireGuideFilter.filterByPrice(entries, WireGuideFilter.Price.FREE)
        val paid = WireGuideFilter.filterByPrice(entries, WireGuideFilter.Price.PAID)
        val all = WireGuideFilter.filterByPrice(entries, null)
        assertEquals(entries.size, all.size)
        assertTrue(free.isNotEmpty() && free.size < entries.size)
        assertTrue(paid.isNotEmpty() && paid.size < entries.size)
        // disjoint classes: no entry is both free and paid
        assertEquals(
            emptySet<String>(),
            free.map { it.id }.toSet() intersect paid.map { it.id }.toSet()
        )
        // every survivor has the right class
        assertTrue(free.all { WireGuideFilter.priceOf(it) == WireGuideFilter.Price.FREE })
        assertTrue(paid.all { WireGuideFilter.priceOf(it) == WireGuideFilter.Price.PAID })
        // order preserved (stable rendering)
        assertEquals(
            entries.filter { it.id in free.map { f -> f.id } },
            free
        )
        // composes with the search path exactly like the buttons do
        val freeMesh = WireGuideFilter.filterByPrice(
            WireGuideFilter.filter(entries, "mesh"), WireGuideFilter.Price.FREE
        )
        assertTrue(freeMesh.all { it.category == GuideCategory.MESH })
        assertEquals(WireGuideCatalog.byCategory(GuideCategory.MESH).size, freeMesh.size)
    }

    @Test
    fun `multi token query requires every token`() {
        // v1.8.6 — pivpn matches by its own keywords ("raspberry", "pi");
        // wirehole joins via the self-hosted category terms ("raspberry")
        // plus its curated "pi-hole" keyword — both haystacks genuinely
        // hold every token, in stable catalog order.
        assertEquals(
            listOf("pivpn", "wirehole"),
            WireGuideFilter.filter(entries, "raspberry pi").map { it.id }
        )

        // tokens spanning different entries must not AND together
        assertTrue(WireGuideFilter.filter(entries, "raspberry nordvpn").isEmpty())
    }

    @Test
    fun `category filter restricts results and composes with query`() {
        val paidCount = WireGuideCatalog.byCategory(GuideCategory.PAID).size
        assertEquals(
            paidCount,
            WireGuideFilter.filter(entries, "", GuideCategory.PAID).size
        )
        // query inside a restricted category
        val nord = WireGuideFilter.filter(entries, "nord", GuideCategory.PAID)
        assertEquals(listOf("nordvpn"), nord.map { it.id })
        // query matches nothing in a restricted category → empty
        assertTrue(
            WireGuideFilter.filter(entries, "warp", GuideCategory.PAID).isEmpty()
        )
    }

    @Test
    fun `url fragments match`() {
        assertTrue(
            WireGuideFilter.filter(entries, "gl-inet").map { it.id }.contains("glinet")
        )
        assertTrue(
            WireGuideFilter.filter(entries, "apps.apple.com")
                .all { it.category == GuideCategory.APPS }
        )
    }

    @Test
    fun `every entry is reachable through its own keywords`() {
        entries.forEach { entry ->
            val keywords = WireGuideCatalog.keywordsOf(entry.id)
            assertTrue("No keywords for ${entry.id}", keywords.isNotEmpty())
            val first = keywords.first()
            assertTrue(
                "Keyword '$first' does not match ${entry.id}",
                WireGuideFilter.matches(entry, first)
            )
        }
    }

    @Test
    fun `match is case insensitive`() {
        val mullvad = WireGuideCatalog.entries.associateBy { it.id }.getValue("mullvad")
        assertTrue(WireGuideFilter.matches(mullvad, "MULLVAD"))
        assertFalse(
            WireGuideFilter.filter(entries, "zzznothing").isNotEmpty()
        )
    }

    // ------------------------------------------------------------------
    // v1.8.1 — user signals: notes search, favorites restriction, sorting
    // ------------------------------------------------------------------

    @Test
    fun `personal note text is searchable`() {
        val notes = mapOf("mullvad" to "my night server inBerlin")
        val hits = WireGuideFilter.filter(entries, "night server", notes = notes)
        assertEquals(listOf("mullvad"), hits.map { it.id })
        // same query without the note must not match by catalog data alone
        assertTrue(
            WireGuideFilter.filter(entries, "night server").isEmpty()
        )
    }

    @Test
    fun `note search combines with catalog haystack and AND semantics`() {
        val notes = mapOf("mullvad" to "backup")
        // token in catalog (name) + token in note → match
        assertEquals(
            listOf("mullvad"),
            WireGuideFilter.filter(entries, "mullvad backup", notes = notes).map { it.id }
        )
        // both tokens only in note → also fine (tokens AND over whole haystack)
        assertEquals(
            listOf("mullvad"),
            WireGuideFilter.filter(entries, "backup", notes = notes).map { it.id }
        )
        // note token that fails on a different entry must not leak it in
        assertTrue(
            WireGuideFilter.filter(entries, "ivpn backup", notes = notes).isEmpty()
        )
    }

    @Test
    fun `favorites restriction keeps only bookmarked ids`() {
        val favorites = setOf("mullvad", "wg-android")
        val hits = WireGuideFilter.filter(entries, "", favorites = favorites)
        assertEquals(favorites, hits.map { it.id }.toSet())
        // composes with query and category
        assertEquals(
            listOf("mullvad"),
            WireGuideFilter.filter(entries, "mull", favorites = favorites).map { it.id }
        )
        assertTrue(
            WireGuideFilter.filter(entries, "", GuideCategory.PAID, null, setOf("wg-android")).isEmpty()
        )
    }

    @Test
    fun `empty favorites set yields empty results`() {
        assertTrue(WireGuideFilter.filter(entries, "", favorites = emptySet()).isEmpty())
    }

    @Test
    fun `sorting floats favorites first then rating desc`() {
        val store = InMemoryGuideStore()
        val order = entries.map { it.id }
        // mid-catalog entries get signals
        store.setFavorite("windscribe", true)                 // favorite → first
        store.setRating("ivpn", 4)                            // top rating → next
        store.setRating("azirevpn", 5)                        // higher rating → before ivpn
        val sorted = WireGuideFilter.sortWithSignals(entries, store).map { it.id }
        assertEquals("windscribe", sorted.first())            // favorite beats rating
        assertEquals("azirevpn", sorted[1])                   // 5 stars
        assertEquals("ivpn", sorted[2])                       // 4 stars
        // v1.8.2 — below the rated block, "Editor's choice" entries lead
        // (catalog order inside the block), then everyone else.
        val editorsButIvpn = WireGuideCatalog.editorsChoice - "ivpn"
        val editorsBlock = order.filter { it in editorsButIvpn }
        val rest = order.filter {
            it !in setOf("windscribe", "ivpn", "azirevpn") && it !in editorsButIvpn
        }
        assertEquals(editorsBlock + rest, sorted.drop(3))
    }

    @Test
    fun `sorting with no signals floats editors choice then catalog order`() {
        val order = entries.map { it.id }
        val editors = WireGuideCatalog.editorsChoice
        val sorted = WireGuideFilter.sortWithSignals(entries, InMemoryGuideStore()).map { it.id }
        assertEquals(
            "editor's choice block first (catalog order), then the rest",
            order.filter { it in editors } + order.filter { it !in editors },
            sorted
        )
    }

    @Test
    fun `top rated ordering puts stars above editors choice above unrated`() {
        val store = InMemoryGuideStore()
        store.setRating("cloudflare-warp-wgcf", 3)
        var sorted = WireGuideFilter.sortWithSignals(entries, store).map { it.id }
        // a 3-star non-editor entry beats all 0-star editors-choice holders
        assertEquals("cloudflare-warp-wgcf", sorted.first())
        // …and a favorite still leads everything
        store.setFavorite("wg-macos", true)
        sorted = WireGuideFilter.sortWithSignals(entries, store).map { it.id }
        assertEquals("wg-macos", sorted.first())
    }

    @Test
    fun `editors choice badge terms are searchable in english and arabic`() {
        assertEquals(
            WireGuideCatalog.editorsChoice,
            WireGuideFilter.filter(entries, "editor").map { it.id }.toSet()
        )
        assertEquals(
            WireGuideCatalog.editorsChoice,
            WireGuideFilter.filter(entries, "موثوق").map { it.id }.toSet()
        )
        assertEquals(
            WireGuideCatalog.editorsChoice,
            WireGuideFilter.filter(entries, "اختيار المحرر").map { it.id }.toSet()
        )
        // non-editor entries stay unreachable through the badge terms
        assertFalse(
            WireGuideFilter.filter(entries, "editor")
                .any { !WireGuideCatalog.isEditorsChoice(it.id) }
        )
    }

    @Test
    fun `v1_8_14 difficulty terms are searchable in english and arabic`() {
        // Arabic: "سهل" (easy) reaches every beginner source only,
        // "متقدم" (advanced) every advanced source only,
        // "متوسط" (intermediate) every intermediate source only
        fun idsOf(query: String) =
            WireGuideFilter.filter(entries, query).map { it.id }.toSet()
        fun classIds(difficulty: GuideDifficulty) =
            entries.filter { it.difficulty == difficulty }.map { it.id }.toSet()

        assertEquals(classIds(GuideDifficulty.BEGINNER), idsOf("سهل"))
        assertEquals(classIds(GuideDifficulty.ADVANCED), idsOf("متقدم"))
        assertEquals(classIds(GuideDifficulty.INTERMEDIATE), idsOf("متوسط"))
        // English mirrors the same classes ("beginner"/"advanced" are
        // collision-free; note "easy" also reaches wg-easy — by its own
        // name — which is honest search behaviour, so it is not asserted
        // as an exact class match)
        assertEquals(classIds(GuideDifficulty.BEGINNER), idsOf("beginner"))
        assertEquals(classIds(GuideDifficulty.ADVANCED), idsOf("advanced"))
        // literal anchors across sections: the hosted mesh pair is easy,
        // the router firmware grind is advanced
        assertTrue(idsOf("سهل").containsAll(listOf("tailscale", "netbird", "mullvad", "glinet")))
        assertTrue(idsOf("متقدم").containsAll(listOf("headscale", "netmaker", "openwrt", "vyos")))
        assertTrue(idsOf("متوسط").containsAll(listOf("firezone", "netgate-appliance", "keenetic")))
        // no beginner source is reachable through the advanced term
        assertFalse(idsOf("متقدم").any { id ->
            WireGuideCatalog.byId(id)?.difficulty == GuideDifficulty.BEGINNER
        })
    }
}
