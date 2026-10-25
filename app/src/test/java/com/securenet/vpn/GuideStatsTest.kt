package com.securenet.vpn

import com.securenet.vpn.guide.GuideSignalsData
import com.securenet.vpn.guide.GuideStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.3 — guide statistics: aggregation (counts, average, distribution)
 * and the "most rated sources" ranking the stats card is headed by.
 * Ranking rules: stars high → low, "Editor's choice" breaks the tie,
 * favorites next, then the plain name; unknown catalog ids degrade to
 * their raw id as the display name.
 */
class GuideStatsTest {

    private fun signals(
        ratings: Map<String, Int> = emptyMap(),
        favorites: Set<String> = emptySet(),
        notes: Map<String, String> = emptyMap()
    ) = GuideSignalsData(ratings, favorites, notes)

    @Test
    fun `empty signals produce an empty summary`() {
        val summary = GuideStats.compute(signals(), 36)
        assertEquals(0, summary.ratedCount)
        assertEquals(0.0, summary.averageStars, 1e-9)
        assertEquals(0, summary.favoritesCount)
        assertEquals(0, summary.notesCount)
        assertTrue(summary.distribution.isEmpty())
        assertTrue(summary.mostRated.isEmpty())
        assertTrue(summary.isEmpty())
        assertEquals(36, summary.totalSources)
    }

    @Test
    fun `counts average and distribution are aggregated`() {
        val summary = GuideStats.compute(
            signals(
                ratings = mapOf("mullvad" to 5, "ivpn" to 4, "pia" to 5),
                favorites = setOf("mullvad", "tailscale"),
                notes = mapOf("mullvad" to "my pick")
            ),
            36
        )
        assertEquals(3, summary.ratedCount)
        assertEquals(14.0 / 3, summary.averageStars, 1e-9)
        assertEquals(2, summary.favoritesCount)
        assertEquals(1, summary.notesCount)
        assertEquals(2, summary.distribution[5])
        assertEquals(1, summary.distribution[4])
        assertEquals(null, summary.distribution[3])
        assertFalse(summary.isEmpty())
    }

    @Test
    fun `most rated ranks stars first, editors choice second, name last`() {
        // windscribe 5★ (not editor's choice), mullvad 5★ (editor's choice),
        // ivpn + tailscale 4★ (both editor's choice) → ivpn before tailscale.
        val summary = GuideStats.compute(
            signals(
                ratings = mapOf(
                    "tailscale" to 4, "windscribe" to 5,
                    "ivpn" to 4, "mullvad" to 5
                )
            ),
            36
        )
        val order = summary.mostRated.map { it.id }
        assertEquals(listOf("mullvad", "windscribe", "ivpn", "tailscale"), order)
        assertTrue(summary.mostRated[0].editorsChoice)
        assertFalse(summary.mostRated[1].editorsChoice)
    }

    @Test
    fun `favorite badge rides the ranked rows`() {
        val summary = GuideStats.compute(
            signals(ratings = mapOf("mullvad" to 5), favorites = setOf("mullvad")),
            36
        )
        val row = summary.mostRated.single()
        assertEquals("mullvad", row.id)
        assertTrue(row.favorite)
        assertEquals(5, row.stars)
    }

    @Test
    fun `limit caps the ranked list`() {
        val ratings = mapOf(
            "a" to 1, "b" to 2, "c" to 3, "d" to 4,
            "e" to 5, "f" to 5, "g" to 4
        )
        val summary = GuideStats.compute(signals(ratings = ratings), 36, limit = 3)
        assertEquals(3, summary.mostRated.size)
        assertEquals(5, summary.mostRated.first().stars)
        assertEquals(7, summary.ratedCount) // cap affects display only
    }

    @Test
    fun `default limit is five`() {
        val ratings = (1..7).associate { "src$it" to ((it % 5) + 1) }
        val summary = GuideStats.compute(signals(ratings = ratings), 36)
        assertEquals(GuideStats.DEFAULT_LIMIT, summary.mostRated.size)
    }

    @Test
    fun `unknown catalog ids degrade to the raw id as name`() {
        val summary = GuideStats.compute(
            signals(ratings = mapOf("ghost-id" to 3)),
            36
        )
        assertEquals("ghost-id", summary.mostRated.single().name)
    }

    @Test
    fun `known ids resolve to their catalog names`() {
        val summary = GuideStats.compute(
            signals(ratings = mapOf("mullvad" to 4)),
            36
        )
        val name = summary.mostRated.single().name
        assertTrue(name.isNotBlank() && name != "mullvad")
    }

    @Test
    fun `out of range stars never appear in the distribution`() {
        val summary = GuideStats.compute(
            signals(ratings = mapOf("x" to 0, "y" to 9, "z" to 3)),
            36
        )
        // 0 and 9 are impossible after normalisation, but the aggregator
        // still filters defensively; only the 3★ survives.
        assertEquals(3, summary.ratedCount)
        assertEquals(null, summary.distribution[0])
        assertEquals(null, summary.distribution[9])
        assertEquals(1, summary.distribution[3])
    }
}
