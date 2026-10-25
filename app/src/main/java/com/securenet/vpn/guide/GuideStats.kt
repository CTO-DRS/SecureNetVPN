package com.securenet.vpn.guide

/**
 * v1.8.3 — statistics for the WireGuard config-sources guide, headed by the
 * "most rated sources" ranking the user asked for ("إحصاءات «أكثر المصادر
 * تقييمًا»"). Because the signals are per-device, "most rated" means the
 * sources this user starred, ranked by stars (high → low) with the
 * "Editor's choice" reliability badge and the source name breaking ties —
 * the same ordering philosophy as [WireGuideFilter.sortWithSignals].
 *
 * Everything is computed from an immutable [GuideSignalsData] snapshot, so
 * the whole object is pure Kotlin (no Android imports) and fully
 * unit-testable on the JVM.
 */
object GuideStats {

    /** One ranked row of the "most rated sources" list. */
    data class Ranked(
        val id: String,
        val name: String,
        /** Star rating 1..5. */
        val stars: Int,
        val favorite: Boolean,
        val editorsChoice: Boolean
    )

    /**
     * Aggregated snapshot of the user's guide activity.
     *
     * @param totalSources      entries in the catalog (36 as of v1.8.2).
     * @param ratedCount        sources carrying a 1–5 star rating.
     * @param averageStars      mean of the given ratings, 0.0 when unrated.
     * @param favoritesCount    bookmarked sources.
     * @param notesCount        sources carrying a personal note.
     * @param distribution      stars 1..5 → how many sources got that value;
     *                          absent keys mean zero.
     * @param mostRated         the ranked "most rated" list, at most [limit]
     *                          rows, best first.
     */
    data class Summary(
        val totalSources: Int,
        val ratedCount: Int,
        val averageStars: Double,
        val favoritesCount: Int,
        val notesCount: Int,
        val distribution: Map<Int, Int>,
        val mostRated: List<Ranked>
    ) {
        /** True when the user has produced no signals at all. */
        fun isEmpty(): Boolean =
            ratedCount == 0 && favoritesCount == 0 && notesCount == 0
    }

    /**
     * Aggregates [signals] against a catalog of [totalSources] entries.
     * [limit] caps the ranked list shown in the stats card (top 5 default).
     */
    fun compute(
        signals: GuideSignalsData,
        totalSources: Int,
        limit: Int = DEFAULT_LIMIT
    ): Summary {
        val distribution = mutableMapOf<Int, Int>()
        signals.ratings.values.forEach { stars ->
            if (stars in 1..5) distribution[stars] = (distribution[stars] ?: 0) + 1
        }
        val average = if (signals.ratings.isEmpty()) 0.0
        else signals.ratings.values.sum().toDouble() / signals.ratings.size

        val ranked = signals.ratings.entries
            .map { (id, stars) ->
                Ranked(
                    id = id,
                    name = WireGuideCatalog.byId(id)?.name ?: id,
                    stars = stars,
                    favorite = id in signals.favorites,
                    editorsChoice = WireGuideCatalog.isEditorsChoice(id)
                )
            }
            .sortedWith(
                compareByDescending<Ranked> { it.stars }
                    .thenByDescending { it.editorsChoice }
                    .thenByDescending { it.favorite }
                    .thenBy { it.name.lowercase() }
            )
            .take(limit.coerceAtLeast(0))

        return Summary(
            totalSources = totalSources,
            ratedCount = signals.ratings.size,
            averageStars = average,
            favoritesCount = signals.favorites.size,
            notesCount = signals.notes.size,
            distribution = distribution,
            mostRated = ranked
        )
    }

    const val DEFAULT_LIMIT = 5
}
