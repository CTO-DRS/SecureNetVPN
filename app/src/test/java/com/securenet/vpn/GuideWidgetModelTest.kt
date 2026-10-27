package com.securenet.vpn

import com.securenet.vpn.guide.GuideSmart
import com.securenet.vpn.guide.GuideSignalsData
import com.securenet.vpn.guide.GuideWidgetModel
import com.securenet.vpn.guide.WireGuideCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.11 — tests for the home-screen widget's pure view-model: the rows
 * it shows, the empty state, and its determinism contract. All JVM — the
 * provider only maps these rows onto RemoteViews.
 */
class GuideWidgetModelTest {

    private val entries = WireGuideCatalog.entries
    private val byId = entries.associateBy { it.id }

    private fun signals(
        ratings: Map<String, Int> = emptyMap(),
        favorites: Set<String> = emptySet(),
        notes: Map<String, String> = emptyMap()
    ) = GuideSignalsData(ratings, favorites, notes)

    /** The ten Editor's-choice ids, straight from the catalog contract. */
    private val badged = listOf(
        "mullvad", "ivpn", "proton-free",
        "wg-easy", "pivpn", "tailscale",
        "openwrt", "wg-android", "wg-fdroid", "wg-desktop"
    )

    /** The seven APPS entries (3 badged + 4 plain). */
    private val appsIds = listOf(
        "wg-android", "wg-fdroid", "wg-desktop",
        "wg-ios", "wg-macos", "wiresock", "networkmanager"
    )

    @Test
    fun `cold start shows unrated editors choice picks`() {
        val content = GuideWidgetModel.build(signals())
        assertTrue(content.rows.isNotEmpty())
        assertTrue("cold start caps at three rows", content.rows.size <= 3)
        content.rows.forEach { row ->
            assertTrue("${row.sourceId} must be badged", WireGuideCatalog.isEditorsChoice(row.sourceId))
            assertEquals(GuideSmart.Reason.COLD_START, row.reason)
            assertEquals(0, row.score)
        }
    }

    @Test
    fun `rows mirror the guide recommendations exactly`() {
        val s = signals(
            ratings = mapOf("windscribe" to 4),
            favorites = setOf("mullvad"),
            notes = mapOf("ivpn" to "fast")
        )
        val expected = GuideSmart.recommendations(s).map { it.entry.id }
        assertEquals(expected, GuideWidgetModel.build(s).rows.map { it.sourceId })
    }

    @Test
    fun `rows never recommend bookmarked sources`() {
        val s = signals(
            ratings = mapOf("windscribe" to 4),
            favorites = setOf("mullvad", "ivpn", "azirevpn", "ovpn"),
            notes = mapOf("ivpn" to "fast")
        )
        GuideWidgetModel.build(s).rows.forEach { row ->
            assertTrue("${row.sourceId} must not be a favorite",
                row.sourceId !in s.favorites)
        }
    }

    @Test
    fun `widget rows are capped at the requested limit`() {
        val s = signals(ratings = mapOf("windscribe" to 4))
        val content = GuideWidgetModel.build(s, limit = 2)
        assertEquals(2, content.rows.size)
    }

    @Test
    fun `a row carries the catalog name reason and score`() {
        // windscribe rated → PAID is the top category with 1 signal;
        // mullvad (unrated, badged, not bookmarked) scores 2×1 + 1 = 3.
        val s = signals(ratings = mapOf("windscribe" to 4))
        val row = GuideWidgetModel.build(s).rows.first { it.sourceId == "mullvad" }
        assertEquals("Mullvad VPN", row.name)
        assertEquals(GuideSmart.Reason.TOP_CATEGORY, row.reason)
        assertEquals(3, row.score)
    }

    @Test
    fun `empty state fires when every entry is excluded`() {
        // All badged sources rated + every APPS source bookmarked: the top
        // category (APPS, boosted by its favorites) is fully bookmarked and
        // every badged source elsewhere is already rated — nothing scores.
        val s = signals(
            ratings = badged.associateWith { 5 },
            favorites = appsIds.toSet()
        )
        val content = GuideWidgetModel.build(s)
        assertTrue("widget must fall back to the hint line", content.empty)
        assertTrue(content.rows.isEmpty())
    }

    @Test
    fun `cold start is never empty`() {
        assertFalse(GuideWidgetModel.build(signals()).empty)
    }

    @Test
    fun `same signals always produce the same rows`() {
        val s = signals(
            ratings = mapOf("windscribe" to 4, "wg-easy" to 2),
            favorites = setOf("mullvad")
        )
        assertEquals(
            GuideWidgetModel.build(s).rows,
            GuideWidgetModel.build(s).rows
        )
    }

    @Test
    fun `zero limit degrades to the empty state`() {
        val content = GuideWidgetModel.build(signals(), limit = 0)
        assertTrue(content.empty)
        assertTrue(content.rows.isEmpty())
    }
}
