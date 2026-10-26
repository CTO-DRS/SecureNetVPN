package com.securenet.vpn

import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideDetail
import com.securenet.vpn.guide.InMemoryGuideStore
import com.securenet.vpn.guide.WireGuideCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.7 — pure-logic tests behind the per-source detail page: the merged
 * catalog+signals facts, the "more in this category" suggestions and the
 * share text. Uses [InMemoryGuideStore] so no Android runtime is needed.
 */
class GuideDetailTest {

    private val byId = WireGuideCatalog.entries.associateBy { it.id }

    @Test
    fun `facts merge catalog data with user signals`() {
        val store = InMemoryGuideStore()
        val mullvad = byId.getValue("mullvad")

        val empty = GuideDetail.facts(mullvad, store)
        assertEquals(0, empty.rating)
        assertFalse(empty.favorite)
        assertEquals(null, empty.note)
        assertTrue("mullvad is on the curated shortlist", empty.editorsChoice)
        assertFalse(empty.hasSignals)

        store.setRating("mullvad", 5)
        store.setFavorite("mullvad", true)
        store.setNote("mullvad", "my everyday provider")
        val full = GuideDetail.facts(mullvad, store)
        assertEquals(5, full.rating)
        assertTrue(full.favorite)
        assertEquals("my everyday provider", full.note)
        assertTrue(full.hasSignals)
    }

    @Test
    fun `facts flag editors choice correctly`() {
        val store = InMemoryGuideStore()
        val openwrt = byId.getValue("openwrt")       // on the shortlist
        val cryptostorm = byId.getValue("cryptostorm") // not on it
        assertTrue(GuideDetail.facts(openwrt, store).editorsChoice)
        assertFalse(GuideDetail.facts(cryptostorm, store).editorsChoice)
    }

    @Test
    fun `related lists same-category siblings excluding self`() {
        val store = InMemoryGuideStore()
        val pivpn = byId.getValue("pivpn")
        val related = GuideDetail.related(pivpn, store)
        assertTrue(related.isNotEmpty())
        assertTrue("self must not appear in its own related list",
            related.none { it.id == "pivpn" })
        assertTrue(related.all { it.category == GuideCategory.SELF_HOSTED })
        assertEquals(GuideDetail.related(pivpn, store, limit = 5).size, related.size)
        assertTrue("default limit is 5", related.size <= 5)
    }

    @Test
    fun `related respects a custom limit`() {
        val store = InMemoryGuideStore()
        val mullvad = byId.getValue("mullvad") // PAID: 20 members → 19 siblings
        assertEquals(3, GuideDetail.related(mullvad, store, limit = 3).size)
        assertEquals(19, GuideDetail.related(mullvad, store, limit = 99).size)
    }

    @Test
    fun `related ranks the user's favourites and ratings first`() {
        val store = InMemoryGuideStore()
        val mullvad = byId.getValue("mullvad")
        // Bookmark a late-catalog sibling: it must lead the related list.
        store.setFavorite("cryptostorm", true)
        val related = GuideDetail.related(mullvad, store)
        assertEquals("cryptostorm", related.first().id)
        // Second favourite outranks a five-star unrated rival? No — stars
        // come first: favorite(1) > favorite(0), then rating desc.
        store.setRating("njalla", 5)
        val reranked = GuideDetail.related(mullvad, store)
        assertEquals("cryptostorm", reranked[0].id)  // favorite still first
        assertEquals("njalla", reranked[1].id)       // then the 5-star entry
    }

    @Test
    fun `related works for the smallest category`() {
        val store = InMemoryGuideStore()
        val free = WireGuideCatalog.byCategory(GuideCategory.FREE)
        val related = GuideDetail.related(free.first(), store)
        assertEquals("FREE had ${free.size} members", free.size - 1, related.size)
    }

    @Test
    fun `share text joins name and url with an em dash`() {
        val wgandroid = byId.getValue("wg-android")
        assertEquals(
            "WireGuard for Android (Google Play) — " +
                "https://play.google.com/store/apps/details?id=com.wireguard.android",
            GuideDetail.shareText(wgandroid)
        )
    }

    @Test
    fun `every catalog id resolves through the detail entry point`() {
        // The detail fragment looks entries up by id (fragment argument);
        // a catalog drift would leave users on the "unknown source" path.
        WireGuideCatalog.entries.forEach { entry ->
            assertEquals(entry, WireGuideCatalog.byId(entry.id))
        }
    }
}
