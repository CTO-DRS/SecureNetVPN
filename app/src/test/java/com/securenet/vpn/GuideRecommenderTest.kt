package com.securenet.vpn

import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideDifficulty
import com.securenet.vpn.guide.GuideRecommender
import com.securenet.vpn.guide.WireGuideCatalog
import com.securenet.vpn.guide.WireGuideFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.19 — pins the 🧭 setup recommender to the shipped catalog: the
 * difficulty ceiling is absolute (a source harder than the user's level
 * never surfaces), the budget filter speaks the same free/paid markers as
 * the quick filter, the section filter restricts, the list is capped at
 * five and fully deterministic, and every suggestion carries at least one
 * human-readable reason. Catalog anchors use only entries whose difficulty
 * and price the release notes state explicitly (Tailscale = mesh/beginner/
 * free, Headscale = mesh/advanced).
 */
class GuideRecommenderTest {

    private val entries = WireGuideCatalog.entries

    @Test
    fun `beginner shortlist never contains a harder source`() {
        val picks = GuideRecommender.recommend(
            entries, GuideDifficulty.BEGINNER, GuideRecommender.Budget.ANY, null
        )
        assertTrue(picks.isNotEmpty())
        assertTrue(picks.all { it.entry.difficulty == GuideDifficulty.BEGINNER })
    }

    @Test
    fun `intermediate shortlist stays at or below intermediate`() {
        val picks = GuideRecommender.recommend(
            entries, GuideDifficulty.INTERMEDIATE, GuideRecommender.Budget.ANY, null
        )
        assertTrue(picks.all { it.entry.difficulty <= GuideDifficulty.INTERMEDIATE })
    }

    @Test
    fun `advanced shortlist includes the honest ops projects`() {
        val picks = GuideRecommender.recommend(
            entries, GuideDifficulty.ADVANCED, GuideRecommender.Budget.ANY, null
        )
        assertTrue(picks.map { it.entry.id }.contains("headscale"))
    }

    @Test
    fun `free-only budget keeps only free-marked sources`() {
        val picks = GuideRecommender.recommend(
            entries, GuideDifficulty.BEGINNER, GuideRecommender.Budget.FREE_ONLY, null
        )
        assertTrue(picks.isNotEmpty())
        picks.forEach { suggestion ->
            assertEquals(
                WireGuideFilter.Price.FREE,
                WireGuideFilter.priceOf(suggestion.entry)
            )
        }
        assertTrue(picks.map { it.entry.id }.contains("tailscale"))
    }

    @Test
    fun `section choice restricts the shortlist to that section`() {
        val picks = GuideRecommender.recommend(
            entries, GuideDifficulty.BEGINNER, GuideRecommender.Budget.ANY, GuideCategory.MESH
        )
        assertTrue(picks.isNotEmpty())
        assertTrue(picks.all { it.entry.category == GuideCategory.MESH })
        assertTrue(picks.map { it.entry.id }.contains("tailscale"))
    }

    @Test
    fun `shortlist is capped at five and strictly score-ordered`() {
        val picks = GuideRecommender.recommend(
            entries, GuideDifficulty.ADVANCED, GuideRecommender.Budget.ANY, null
        )
        assertTrue(picks.size <= 5)
        assertEquals(picks.size, picks.map { it.entry.id }.toSet().size)
        assertTrue(
            picks.zipWithNext().all { (a, b) -> a.score >= b.score }
        )
    }

    @Test
    fun `same answers always produce the same list`() {
        val first = GuideRecommender.recommend(
            entries, GuideDifficulty.INTERMEDIATE, GuideRecommender.Budget.FREE_ONLY, GuideCategory.SELF_HOSTED
        )
        val second = GuideRecommender.recommend(
            entries, GuideDifficulty.INTERMEDIATE, GuideRecommender.Budget.FREE_ONLY, GuideCategory.SELF_HOSTED
        )
        assertEquals(first, second)
    }

    @Test
    fun `every suggestion carries at least one visible reason`() {
        val picks = GuideRecommender.recommend(
            entries, GuideDifficulty.BEGINNER, GuideRecommender.Budget.ANY, null
        )
        assertTrue(picks.all { it.reasons.isNotEmpty() })
        picks.forEach { suggestion ->
            if (GuideRecommender.Reason.EDITORS_CHOICE in suggestion.reasons) {
                assertTrue(WireGuideCatalog.isEditorsChoice(suggestion.entry.id))
            }
            if (suggestion.entry.difficulty == GuideDifficulty.BEGINNER) {
                assertTrue(GuideRecommender.Reason.LEVEL_MATCH in suggestion.reasons)
            }
        }
    }

    @Test
    fun `impossible ask yields an empty shortlist not a crash`() {
        // free-only inside the paid-providers section: no source carries a
        // free marker there by design, so the honest answer is an empty list.
        val picks = GuideRecommender.recommend(
            entries, GuideDifficulty.BEGINNER, GuideRecommender.Budget.FREE_ONLY, GuideCategory.PAID
        )
        assertTrue(picks.isEmpty())
    }
}
