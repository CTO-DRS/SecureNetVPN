package com.securenet.vpn.guide

import com.securenet.vpn.R

/**
 * v1.8.19 — 🧭 "pick for me" setup recommender (مساعد اختيار المصدر). The
 * guide has 69 sources across six sections; a first-time user often knows
 * only three things about themselves: how technical they are, whether they
 * insist on free options, and which kind of setup interests them. This
 * engine turns exactly those three answers into a ranked shortlist —
 * no account, no telemetry, no cloud: pure deterministic scoring over the
 * shipped catalog.
 *
 * Scoring (deliberately simple and explainable, every point is a visible
 * reason): sources harder than the user's level never surface; matching
 * the level beats an easier source (30 vs 15); free sources get a small
 * nudge (+10) and Editor's-choice sources a trust nudge (+15); ties break
 * by id so the same answers always produce the same list. At most five
 * suggestions — a shortlist, not a second catalog.
 *
 * Pure data in / data out ([GuideEntry] + reason enums, zero Android APIs),
 * so the whole contract is JVM-tested ([GuideRecommenderTest] pins the
 * difficulty ceiling, the budget filter, the section filter, the cap, the
 * determinism and the reason anchors).
 */
object GuideRecommender {

    /** What the user is willing to pay. */
    enum class Budget {
        /** Only sources that cost nothing — [WireGuideFilter.Price.FREE]. */
        FREE_ONLY,

        /** Free or paid, whatever fits best. */
        ANY
    }

    /**
     * Why a source made the shortlist — each reason maps to one localized
     * line in the UI ([reasonLabelRes]), so a suggestion never shows a bare
     * score the user cannot understand.
     */
    enum class Reason {
        /** Difficulty exactly equals the user's level. */
        LEVEL_MATCH,

        /** Difficulty is below the user's level — an easy start. */
        EASIER_STEP,

        /** Free per the same markers the free/paid filter speaks. */
        FREE,

        /** Editor's-choice trust marker from the catalog. */
        EDITORS_CHOICE
    }

    /** One ranked suggestion: the source, its score and the visible reasons. */
    data class Suggestion(
        val entry: GuideEntry,
        val score: Int,
        val reasons: List<Reason>
    )

    /**
     * Ranks [entries] for a user at [level] (sources harder than the level
     * are excluded outright), honoring [budget] and an optional [section]
     * (null = all sections). Deterministic: same inputs, same list, capped
     * at five suggestions.
     */
    fun recommend(
        entries: List<GuideEntry>,
        level: GuideDifficulty,
        budget: Budget,
        section: GuideCategory?
    ): List<Suggestion> {
        val pool = entries.filter { entry ->
            entry.difficulty <= level &&
                (budget == Budget.ANY ||
                    WireGuideFilter.priceOf(entry) == WireGuideFilter.Price.FREE) &&
                (section == null || entry.category == section)
        }
        return pool.map { entry ->
            val reasons = mutableListOf<Reason>()
            var score = 60
            if (entry.difficulty == level) {
                score += 30
                reasons += Reason.LEVEL_MATCH
            } else {
                score += 15
                reasons += Reason.EASIER_STEP
            }
            if (WireGuideFilter.priceOf(entry) == WireGuideFilter.Price.FREE) {
                score += 10
                reasons += Reason.FREE
            }
            if (WireGuideCatalog.isEditorsChoice(entry.id)) {
                score += 15
                reasons += Reason.EDITORS_CHOICE
            }
            Suggestion(entry, score, reasons)
        }.sortedWith(
            compareByDescending<Suggestion> { it.score }.thenBy { it.entry.id }
        ).take(5)
    }

    /** Localized line for one reason — shared by every suggestion row. */
    @Suppress("UNUSED_PARAMETER")
    fun reasonLabelRes(reason: Reason): Int = when (reason) {
        Reason.LEVEL_MATCH -> R.string.recommend_reason_level
        Reason.EASIER_STEP -> R.string.recommend_reason_easier
        Reason.FREE -> R.string.recommend_reason_free
        Reason.EDITORS_CHOICE -> R.string.recommend_reason_editors
    }
}
