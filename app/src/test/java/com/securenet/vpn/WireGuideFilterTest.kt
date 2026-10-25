package com.securenet.vpn

import com.securenet.vpn.guide.GuideCategory
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
        val free = WireGuideFilter.filter(entries, "مجاني")
        assertTrue(free.isNotEmpty())
        assertTrue(free.all { it.category == GuideCategory.FREE })

        val paid = WireGuideFilter.filter(entries, "مدفوع")
        assertTrue(paid.isNotEmpty())
        assertTrue(paid.all { it.category == GuideCategory.PAID })
    }

    @Test
    fun `multi token query requires every token`() {
        // pivpn is the only entry whose haystack holds both tokens
        val hits = WireGuideFilter.filter(entries, "raspberry pi")
        assertEquals(listOf("pivpn"), hits.map { it.id })

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
}
