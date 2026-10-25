package com.securenet.vpn

import com.securenet.vpn.guide.GuideStore
import com.securenet.vpn.guide.InMemoryGuideStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v1.8.1 — user signals for the config-sources guide (star rating 0–5,
 * favorite flag, personal note). [InMemoryGuideStore] is the pure-JVM
 * reference implementation shared by both persistence backends; the
 * normalisation rules exercised here (clamping, blank-clears, id sets)
 * are exactly the ones [GuideStore] documents, so the SharedPreferences
 * shell inherits them by construction.
 */
class GuideStoreTest {

    private lateinit var store: InMemoryGuideStore

    @Before
    fun setUp() {
        store = InMemoryGuideStore()
    }

    @Test
    fun `defaults are unrated, not favorite, no note`() {
        assertEquals(0, store.rating("mullvad"))
        assertFalse(store.isFavorite("mullvad"))
        assertNull(store.note("mullvad"))
        assertTrue(store.favoriteIds().isEmpty())
        assertTrue(store.noteIds().isEmpty())
    }

    @Test
    fun `rating accepts 1 to 5 and overwrites`() {
        store.setRating("mullvad", 3)
        assertEquals(3, store.rating("mullvad"))
        store.setRating("mullvad", 5)
        assertEquals(5, store.rating("mullvad"))
        store.setRating("mullvad", 1)
        assertEquals(1, store.rating("mullvad"))
    }

    @Test
    fun `rating clamps above five and clears at or below zero`() {
        store.setRating("mullvad", 9)
        assertEquals(5, store.rating("mullvad"))
        store.setRating("mullvad", 0)
        assertEquals(0, store.rating("mullvad"))
        store.setRating("mullvad", 4)
        store.setRating("mullvad", -3)
        assertEquals(0, store.rating("mullvad"))
    }

    @Test
    fun `rating clear removes the id from tracking`() {
        store.setRating("ivpn", 4)
        assertTrue(store.rating("ivpn") > 0)
        store.setRating("ivpn", 0)
        assertEquals(0, store.rating("ivpn"))
    }

    @Test
    fun `favorite toggles and ids follow`() {
        store.setFavorite("mullvad", true)
        store.setFavorite("ivpn", true)
        assertTrue(store.isFavorite("mullvad"))
        assertEquals(setOf("mullvad", "ivpn"), store.favoriteIds())

        store.setFavorite("mullvad", false)
        assertFalse(store.isFavorite("mullvad"))
        assertEquals(setOf("ivpn"), store.favoriteIds())
    }

    @Test
    fun `notes trim whitespace and blank clears`() {
        store.setNote("mullvad", "  my account — best at night  ")
        assertEquals("my account — best at night", store.note("mullvad"))

        store.setNote("mullvad", "   ")
        assertNull(store.note("mullvad"))

        store.setNote("ivpn", "backup server")
        store.setNote("ivpn", null)
        assertNull(store.note("ivpn"))
        assertTrue(store.noteIds().isEmpty())
    }

    @Test
    fun `note and favorite ids are independent sets`() {
        store.setNote("mullvad", "note only")
        store.setFavorite("ivpn", true)
        assertEquals(setOf("mullvad"), store.noteIds())
        assertEquals(setOf("ivpn"), store.favoriteIds())
    }

    @Test
    fun `signals are per-source and do not bleed across ids`() {
        store.setRating("mullvad", 5)
        store.setRating("windscribe", 2)
        store.setFavorite("mullvad", true)
        assertEquals(5, store.rating("mullvad"))
        assertEquals(2, store.rating("windscribe"))
        assertFalse(store.isFavorite("windscribe"))
    }

    @Test
    fun `normalize helpers match the documented rules`() {
        assertEquals(0, GuideStore.normalizeRating(-1))
        assertEquals(0, GuideStore.normalizeRating(0))
        assertEquals(3, GuideStore.normalizeRating(3))
        assertEquals(5, GuideStore.normalizeRating(7))
        assertNull(GuideStore.normalizeNote("   "))
        assertEquals("kept", GuideStore.normalizeNote("  kept "))
        assertNull(GuideStore.normalizeNote(null))
    }
}
