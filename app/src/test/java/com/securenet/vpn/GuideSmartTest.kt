package com.securenet.vpn

import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideSmart
import com.securenet.vpn.guide.GuideSignalsData
import com.securenet.vpn.guide.WireGuideCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.9 — tests for the smart layer: data-grounded recommendations, the
 * typo-tolerant (Levenshtein) search fallback and the analytical insights.
 * All pure JVM — the same inputs the fragment feeds in, via
 * [GuideSignalsData] snapshots built by hand.
 */
class GuideSmartTest {

    private val entries = WireGuideCatalog.entries
    private val byId = entries.associateBy { it.id }

    private fun signals(
        ratings: Map<String, Int> = emptyMap(),
        favorites: Set<String> = emptySet(),
        notes: Map<String, String> = emptyMap()
    ) = GuideSignalsData(ratings, favorites, notes)

    // ---------------- recommendations ----------------

    @Test
    fun `cold start returns unrated editors choice picks`() {
        val picks = GuideSmart.recommendations(signals())
        assertTrue(picks.isNotEmpty())
        assertTrue("cold start is capped", picks.size <= 3)
        picks.forEach { rec ->
            assertTrue("${rec.entry.id} must carry the badge",
                WireGuideCatalog.isEditorsChoice(rec.entry.id))
            assertEquals(GuideSmart.Reason.COLD_START, rec.reason)
            assertEquals(0, rec.score)
        }
    }

    @Test
    fun `top category affinity drives scoring and reason`() {
        val s = signals(ratings = mapOf("pivpn" to 5, "wirehole" to 4, "pritunl" to 3))
        val picks = GuideSmart.recommendations(s)
        assertTrue(picks.isNotEmpty())
        val first = picks.first()
        assertEquals("self-hosted signals dominate", GuideCategory.SELF_HOSTED, first.entry.category)
        assertEquals(GuideSmart.Reason.TOP_CATEGORY, first.reason)
        assertTrue("3 signals x 2 (+1 for an unrated editor's pick)",
            first.score >= 6)
        picks.forEach { rec -> assertTrue(rec.score > 0) }
    }

    @Test
    fun `bookmarked sources are never recommended`() {
        val s = signals(favorites = setOf("pivpn", "wirehole"))
        val picks = GuideSmart.recommendations(s)
        assertTrue(picks.isNotEmpty())
        picks.forEach { rec ->
            assertFalse("${rec.entry.id} is already bookmarked",
                rec.entry.id in s.favorites)
        }
        assertTrue("self-hosted siblings still surface",
            picks.any { it.entry.category == GuideCategory.SELF_HOSTED })
    }

    @Test
    fun `editors choice boost is visible and honest`() {
        val s = signals(ratings = mapOf("mullvad" to 5))   // PAID, editor's choice, now rated
        val picks = GuideSmart.recommendations(s, limit = entries.size)  // every PAID sibling scores > 0
        // ivpn: unrated editor's choice in the dominant category → 2*1 + 1
        val ivpn = picks.first { it.entry.id == "ivpn" }
        assertEquals(GuideSmart.Reason.TOP_CATEGORY, ivpn.reason)
        assertEquals(3, ivpn.score)
        // nordvpn: plain sibling of the dominant category → 2*1
        val nordvpn = picks.first { it.entry.id == "nordvpn" }
        assertEquals(2, nordvpn.score)
        // ivpn outranks nordvpn despite equal catalog affinity.
        assertTrue(picks.indexOfFirst { it.entry.id == "ivpn" } <
            picks.indexOfFirst { it.entry.id == "nordvpn" })
        // The rated editor's choice itself never leaks back in.
        assertTrue(picks.none { it.entry.id == "mullvad" && it.reason == GuideSmart.Reason.EDITORS_CHOICE })
    }

    @Test
    fun `all-favorited catalog degrades to the cold start fallback`() {
        val s = signals(favorites = entries.map { it.id }.toSet())
        val picks = GuideSmart.recommendations(s)
        assertTrue(picks.isNotEmpty())
        picks.forEach { rec ->
            assertEquals(GuideSmart.Reason.COLD_START, rec.reason)
            assertTrue(WireGuideCatalog.isEditorsChoice(rec.entry.id))
        }
    }

    // ---------------- fuzzy search ----------------

    @Test
    fun `fuzzy search finds typo'd brand names`() {
        assertTrue(GuideSmart.fuzzySearch(entries, "wirguard")
            .any { it.id == "wg-android" })
        assertTrue(GuideSmart.fuzzySearch(entries, "norfvpn")
            .any { it.id == "nordvpn" })
        // Exact spelling still matches through the same path.
        assertTrue(GuideSmart.fuzzySearch(entries, "wireguard")
            .any { it.id == "wg-android" })
    }

    @Test
    fun `fuzzy keeps the all-tokens-must-hit semantics`() {
        val results = GuideSmart.fuzzySearch(entries, "raspberry pivn")
        assertTrue(results.any { it.id == "pivpn" })
        results.forEach { entry ->
            assertTrue(GuideSmart.fuzzyMatches(entry, "raspberry pivn"))
        }
        assertTrue("no entry is both raspberry-flavoured and nordvpn-like",
            GuideSmart.fuzzySearch(entries, "raspberry norfvpn").isEmpty())
    }

    @Test
    fun `short tokens stay exact-only`() {
        assertTrue("wg hits wg-easy's url exactly",
            GuideSmart.fuzzySearch(entries, "wg").any { it.id == "wg-easy" })
        assertFalse("2-char token gets no fuzzy budget",
            GuideSmart.fuzzySearch(entries, "wq").any { it.id == "wg-easy" })
    }

    @Test
    fun `blank fuzzy query matches nothing`() {
        assertTrue(GuideSmart.fuzzySearch(entries, "").isEmpty())
        assertTrue(GuideSmart.fuzzySearch(entries, "   ").isEmpty())
    }

    // ---------------- insights ----------------

    @Test
    fun `insights reflect the dominant category and unrated count`() {
        assertTrue(GuideSmart.insights(signals()).isEmpty())

        val s = signals(ratings = mapOf("pivpn" to 5, "wirehole" to 4, "pritunl" to 3))
        val insights = GuideSmart.insights(s)
        assertEquals(2, insights.size)

        val top = insights[0]
        assertEquals(GuideSmart.Type.TOP_CATEGORY, top.type)
        assertEquals(GuideCategory.SELF_HOSTED, top.category)
        assertEquals(3, top.count)

        val unrated = insights[1]
        assertEquals(GuideSmart.Type.UNRATED_IN_TOP_CATEGORY, unrated.type)
        val selfHosted = WireGuideCatalog.byCategory(GuideCategory.SELF_HOSTED).size
        assertEquals(selfHosted - 3, unrated.count)
    }

    // ---------------- levenshtein ----------------

    @Test
    fun `levenshtein distances are sane`() {
        assertEquals(0, GuideSmart.levenshtein("nord", "nord"))
        assertEquals(1, GuideSmart.levenshtein("wireguard", "wirguard"))
        assertEquals(1, GuideSmart.levenshtein("kitten", "sitten"))
        assertEquals(3, GuideSmart.levenshtein("abc", "xyz"))
        assertEquals(2, GuideSmart.levenshtein("", "ab"))
    }
}
