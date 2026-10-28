package com.securenet.vpn

import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideCompare
import com.securenet.vpn.guide.InMemoryGuideStore
import com.securenet.vpn.guide.WireGuideCatalog
import com.securenet.vpn.guide.WireGuideFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.13 — the pure engine behind the side-by-side comparison sheet.
 * The comparison must freeze real catalog facts (section, host, Editor's
 * choice, price class, shared keywords) and rank candidates sensibly —
 * a wrong "shared keywords" line or a candidate list that hides the
 * same-section sources would make the feature lie instead of help.
 */
class GuideCompareTest {

    private val entries = WireGuideCatalog.entries
    private val store = InMemoryGuideStore()

    private fun byId(id: String) = WireGuideCatalog.byId(id)!!

    // ---------------- compare ----------------

    @Test
    fun `compare freezes both entries in argument order`() {
        val cmp = GuideCompare.compare(byId("mullvad"), byId("tailscale"))
        assertEquals("mullvad", cmp.left.id)
        assertEquals("tailscale", cmp.right.id)
        // deterministic: reversed order swaps, never mutates
        val rev = GuideCompare.compare(byId("tailscale"), byId("mullvad"))
        assertEquals("tailscale", rev.left.id)
        assertEquals("mullvad", rev.right.id)
    }

    @Test
    fun `sameCategory is exact and asymmetric cases are honest`() {
        assertTrue(
            GuideCompare.compare(byId("mullvad"), byId("ivpn")).sameCategory
        )
        assertFalse(
            GuideCompare.compare(byId("mullvad"), byId("wg-easy")).sameCategory
        )
    }

    @Test
    fun `sharedKeywords reuse the smart layer vocabulary`() {
        // Sweden links Mullvad / AzireVPN / OVPN — the same anchor the
        // v1.8.10 sibling-reason sweep uses
        val cmp = GuideCompare.compare(byId("mullvad"), byId("azirevpn"))
        assertTrue("sweden" in cmp.sharedKeywords)
        assertEquals(
            com.securenet.vpn.guide.GuideSmart.sharedKeywords(
                byId("mullvad"), byId("azirevpn")
            ),
            cmp.sharedKeywords
        )
        // unrelated sources share nothing — the sheet says so honestly
        val unrelated = GuideCompare.compare(byId("mullvad"), byId("wg-easy"))
        assertTrue(unrelated.sharedKeywords.isEmpty())
    }

    @Test
    fun `hosts are resolved for both columns`() {
        val cmp = GuideCompare.compare(byId("mullvad"), byId("tailscale"))
        assertEquals("mullvad.net", cmp.leftHost)
        assertEquals("tailscale.com", cmp.rightHost)
    }

    @Test
    fun `editors choice flags mirror the catalog badge set`() {
        val cmp = GuideCompare.compare(byId("mullvad"), byId("azirevpn"))
        assertTrue("mullvad carries the badge", cmp.leftEditorsChoice)
        assertFalse("azirevpn does not", cmp.rightEditorsChoice)
    }

    @Test
    fun `price classes match the quick filter classification`() {
        // paid provider vs free mesh tool vs neutral app
        val cmp = GuideCompare.compare(
            byId("mullvad"), byId("tailscale")
        )
        assertEquals(WireGuideFilter.Price.PAID, cmp.leftPrice)
        assertEquals(WireGuideFilter.Price.FREE, cmp.rightPrice)

        val neutral = GuideCompare.compare(byId("wg-android"), byId("mullvad"))
        assertNull(neutral.leftPrice)
        assertEquals(WireGuideFilter.Price.PAID, neutral.rightPrice)
    }

    // ---------------- candidates ----------------

    @Test
    fun `candidates never include the base source`() {
        val base = byId("mullvad")
        val ids = GuideCompare.candidates(base, store).map { it.id }
        assertFalse("mullvad" in ids)
        assertEquals(
            "capped at the default limit",
            15,
            ids.size
        )
        // deterministic across calls
        assertEquals(ids, GuideCompare.candidates(base, store).map { it.id })
    }

    @Test
    fun `same-section sources lead the candidate list`() {
        val base = byId("mullvad")
        val candidates = GuideCompare.candidates(base, store)
        val head = candidates.takeWhile { it.category == GuideCategory.PAID }
        // at least the other paid providers come before any foreign section
        assertTrue(
            "same-section candidates must lead (got ${candidates.take(3).map { it.id }})",
            head.size >= 5
        )
        assertTrue(head.all { it.category == GuideCategory.PAID })
        // the first non-same-section source appears only after them
        assertEquals(
            GuideCategory.PAID,
            candidates.first().category
        )
    }

    @Test
    fun `shared keywords rank candidates within the same section`() {
        val base = byId("mullvad")
        val candidates = GuideCompare.candidates(base, store)
            .filter { it.category == GuideCategory.PAID }
        // AzireVPN and OVPN both share "sweden" — they must outrank a paid
        // provider sharing nothing with Mullvad
        val withSweden = candidates.filter {
            com.securenet.vpn.guide.GuideSmart.sharedKeywords(base, it).isNotEmpty()
        }
        assertTrue(withSweden.map { it.id }.containsAll(listOf("azirevpn", "ovpn")))
        val withoutSweden = candidates.filter {
            com.securenet.vpn.guide.GuideSmart.sharedKeywords(base, it).isEmpty()
        }
        if (withoutSweden.isNotEmpty()) {
            assertTrue(
                "sweden-sharers must outrank non-sharers",
                candidates.indexOf(withSweden.last()) <
                    candidates.indexOf(withoutSweden.first())
            )
        }
    }

    @Test
    fun `user signals lift candidates inside the ordering`() {
        val base = byId("mullvad")
        val rated = InMemoryGuideStore().apply {
            setRating("ovpn", 5)
            setFavorite("ovpn", true)
        }
        val candidates = GuideCompare.candidates(base, rated)
            .filter { it.category == GuideCategory.PAID }
        // ovpn shares "sweden" AND carries the user's signals — it must
        // beat every same-section source with neither
        val plainPaid = candidates.filter { candidate ->
            com.securenet.vpn.guide.GuideSmart.sharedKeywords(base, candidate).isEmpty() &&
                !rated.isFavorite(candidate.id) && rated.rating(candidate.id) == 0
        }
        if (plainPaid.isNotEmpty()) {
            assertTrue(
                "signalled source must outrank plain same-section sources",
                candidates.indexOf(byId("ovpn")) < candidates.indexOf(plainPaid.first())
            )
        }
    }

    @Test
    fun `limit is respected and zero yields empty`() {
        val base = byId("tailscale")
        assertEquals(3, GuideCompare.candidates(base, store, limit = 3).size)
        assertTrue(GuideCompare.candidates(base, store, limit = 0).isEmpty())
    }
}
