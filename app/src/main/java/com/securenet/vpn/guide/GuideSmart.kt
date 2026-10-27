package com.securenet.vpn.guide

/**
 * v1.8.9 — the smart layer behind the guide ("تطوير المميزات الذكية").
 * Everything here is pure Kotlin over an immutable [GuideSignalsData]
 * snapshot — no Android imports, fully unit-testable on the JVM — and every
 * recommendation is grounded in data the user actually produced (stars,
 * favorites, notes) plus the catalog's own Editor's-choice shortlist. No
 * network, no profile harvesting, no opaque scoring: the reason chip shown
 * next to each pick names exactly why it surfaced.
 *
 * Three services:
 *  - [recommendations]: "Smart picks for you" — up to [limit] sources the
 *    user has not bookmarked yet, scored 2×category-affinity (how many of
 *    their signals live in that category) + 1 for an unrated Editor's
 *    choice entry; cold start falls back to unrated Editor's-choice picks.
 *  - [fuzzySearch]: typo-tolerant search fallback with the same
 *    all-tokens-must-hit AND semantics as [WireGuideFilter], but a token
 *    may also match a haystack word within a small Levenshtein distance
 *    ("wirguard" finds WireGuard).
 *  - [insights]: one-line analytical facts about the user's own pattern
 *    (dominant category, unrated sources inside it) rendered in EN/AR by
 *    the statistics card.
 */
object GuideSmart {

    // ------------------------------------------------------------------
    // Recommendations
    // ------------------------------------------------------------------

    /** Why a source surfaced in the smart picks card — or, since v1.8.10,
     *  in the "more in this category" list of a detail page. */
    enum class Reason {
        /** Lives in the category the user signals the most. */
        TOP_CATEGORY,
        /** Editor's-choice reliability pick the user has not rated yet. */
        EDITORS_CHOICE,
        /** No signals yet — a trusted starting point from the shortlist. */
        COLD_START,
        /** v1.8.10 — shares catalog keywords with the source the user is
         *  currently viewing (same brand family, jurisdiction, tech…). */
        SIMILAR
    }

    data class Recommendation(
        val entry: GuideEntry,
        /** Deterministic score; 0 for cold-start picks. */
        val score: Int,
        val reason: Reason
    )

    /**
     * Smart picks for [signals]. Sources the user already bookmarked are
     * never recommended (they know them); ties keep the catalog's curated
     * order via a stable sort. When nothing scores above zero (or the user
     * has no signals at all) the result degrades to unrated Editor's-choice
     * entries — always grounded, never random.
     */
    fun recommendations(
        signals: GuideSignalsData,
        catalog: List<GuideEntry> = WireGuideCatalog.entries,
        limit: Int = 3
    ): List<Recommendation> {
        val byId = catalog.associateBy { it.id }
        val orderById = catalog.withIndex().associate { (index, entry) -> entry.id to index }

        fun coldStart(): List<Recommendation> =
            catalog.filter { entry ->
                WireGuideCatalog.isEditorsChoice(entry.id) &&
                    entry.id !in signals.ratings
            }.take(limit.coerceAtLeast(0)).map { Recommendation(it, 0, Reason.COLD_START) }

        if (signals.isEmpty()) return coldStart()

        val affinity = categoryAffinity(signals, byId)
        val top = affinity.entries.maxWithOrNull(
            compareBy<Map.Entry<GuideCategory, Int>> { it.value }
                .thenBy { it.key.ordinal }   // deterministic on ties
        ) ?: return coldStart()

        val picks = catalog.mapNotNull { entry ->
            if (entry.id in signals.favorites) return@mapNotNull null
            var score = 0
            var reason = Reason.EDITORS_CHOICE
            if (entry.category == top.key) {
                score += top.value * 2
                reason = Reason.TOP_CATEGORY
            }
            if (WireGuideCatalog.isEditorsChoice(entry.id) &&
                entry.id !in signals.ratings
            ) {
                score += 1
            }
            if (score <= 0) return@mapNotNull null
            Recommendation(entry, score, reason)
        }.sortedWith(
            compareByDescending<Recommendation> { it.score }
                .thenBy { orderById[it.entry.id] ?: Int.MAX_VALUE }
        ).take(limit.coerceAtLeast(0))

        return picks.ifEmpty { coldStart() }
    }

    // ------------------------------------------------------------------
    // Detail-page intelligence (v1.8.10)
    // ------------------------------------------------------------------

    /** Which data-grounded statement the detail page makes about a source. */
    enum class VerdictKind {
        /** The viewed source lives in the user's most-engaged category. */
        TOP_CATEGORY,
        /** The viewed source is an unrated Editor's-choice pick. */
        EDITORS_CHOICE
    }

    /** A personalized one-liner rendered under the detail identity card. */
    data class Verdict(
        val kind: VerdictKind,
        /** Category the verdict is about, when applicable. */
        val category: GuideCategory?,
        /** Signals backing the verdict (0 for the Editor's-choice line). */
        val count: Int
    )

    /**
     * The detail page's smart verdict for [entry]: "this fits the category
     * you engage with most (X · N signals)" when it does, else "Editor's
     * pick you haven't rated yet" when applicable, else null — the page
     * stays neutral instead of inventing praise. Cold start (no signals at
     * all) always yields null: a verdict without user data would be noise.
     */
    fun verdict(
        entry: GuideEntry,
        signals: GuideSignalsData,
        catalog: List<GuideEntry> = WireGuideCatalog.entries
    ): Verdict? {
        if (signals.isEmpty()) return null
        val top = categoryAffinity(
            signals, catalog.associateBy { it.id }
        ).entries.maxWithOrNull(
            compareBy<Map.Entry<GuideCategory, Int>> { it.value }
                .thenBy { it.key.ordinal }   // deterministic on ties
        )
        if (top != null && entry.category == top.key) {
            return Verdict(VerdictKind.TOP_CATEGORY, top.key, top.value)
        }
        if (WireGuideCatalog.isEditorsChoice(entry.id) &&
            entry.id !in signals.ratings
        ) {
            return Verdict(VerdictKind.EDITORS_CHOICE, null, 0)
        }
        return null
    }

    /**
     * Why [sibling] surfaced in "more in this category" for the source
     * [current] the user is viewing. Priority: a real relational link
     * ([Reason.SIMILAR] — shared catalog keywords, symmetric) beats the
     * trust nudge ([Reason.EDITORS_CHOICE] — badged but still unrated).
     * A sibling the user already knows or that shares nothing specific
     * gets no chip at all — silence beats filler.
     */
    fun siblingReason(
        sibling: GuideEntry,
        current: GuideEntry,
        signals: GuideSignalsData
    ): Reason? {
        if (sibling.id == current.id) return null
        if (sharedKeywords(sibling, current).isNotEmpty()) return Reason.SIMILAR
        if (WireGuideCatalog.isEditorsChoice(sibling.id) &&
            sibling.id !in signals.ratings
        ) {
            return Reason.EDITORS_CHOICE
        }
        return null
    }

    /**
     * Catalog keywords (EN aliases + Arabic transliterations) that [a] and
     * [b] share, lowercased and trimmed — the raw material behind
     * [Reason.SIMILAR]. Symmetric by construction; empty when the two
     * sources have nothing specific in common.
     */
    fun sharedKeywords(a: GuideEntry, b: GuideEntry): Set<String> =
        WireGuideCatalog.keywordsOf(a.id).mapTo(mutableSetOf()) { it.lowercase().trim() }
            .intersect(
                WireGuideCatalog.keywordsOf(b.id).mapTo(mutableSetOf()) { it.lowercase().trim() }
            )

    /** Signals per category (favorites + ratings + notes), all included. */
    fun categoryAffinity(
        signals: GuideSignalsData,
        byId: Map<String, GuideEntry>
    ): Map<GuideCategory, Int> {
        val affinity = mutableMapOf<GuideCategory, Int>()
        fun bump(id: String) {
            byId[id]?.let { affinity[it.category] = (affinity[it.category] ?: 0) + 1 }
        }
        signals.favorites.forEach(::bump)
        signals.ratings.keys.forEach(::bump)
        signals.notes.keys.forEach(::bump)
        return affinity
    }

    // ------------------------------------------------------------------
    // Typo-tolerant search
    // ------------------------------------------------------------------

    /**
     * A query token matches a haystack word exactly (substring), or — when
     * long enough to make typos meaningful — within an edit distance:
     * ≤ 2 for tokens of 4+ characters, ≤ 1 for 3-character tokens,
     * exact-only for 1–2 character tokens so short queries never explode.
     */
    fun fuzzyMatches(entry: GuideEntry, query: String, note: String? = null): Boolean {
        val tokens = query.trim().lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true
        val hay = haystackWords(entry, note)
        return tokens.all { token -> hay.any { word -> tokenHits(token, word) } }
    }

    /** Fuzzy fallback list for [query] (blank query matches nothing here). */
    fun fuzzySearch(
        entries: List<GuideEntry>,
        query: String,
        notes: Map<String, String>? = null
    ): List<GuideEntry> {
        if (query.isBlank()) return emptyList()
        return entries.filter { fuzzyMatches(it, query, notes?.get(it.id)) }
    }

    private fun tokenHits(token: String, word: String): Boolean {
        if (word.contains(token)) return true          // exact substring
        val budget = when {
            token.length >= 4 -> 2
            token.length == 3 -> 1
            else -> 0
        }
        if (budget == 0) return false
        return levenshtein(token, word) <= budget
    }

    /** Same haystack as [WireGuideFilter] (name + URL + keywords + category
     *  terms + badge terms + personal note), split into bare words. */
    private fun haystackWords(entry: GuideEntry, note: String?): List<String> {
        val text = buildString {
            append(entry.name.lowercase())
            append(' ')
            append(entry.url.lowercase())
            append(' ')
            append(WireGuideCatalog.keywordsOf(entry.id).joinToString(" ") { it.lowercase() })
            append(' ')
            append(WireGuideFilter.categoryTerms(entry.category).joinToString(" ") { it.lowercase() })
            if (WireGuideCatalog.isEditorsChoice(entry.id)) {
                append(" editor's choice editors اختيار المحرر موثوق موثوقة")
            }
            if (!note.isNullOrBlank()) {
                append(' ')
                append(note.lowercase())
            }
        }
        return text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
    }

    /**
     * Classic two-row Levenshtein DP. Pure and tiny — inputs here are
     * single search tokens and catalog words, never paragraphs.
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] +
                    if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,      // deletion
                    current[j - 1] + 1,   // insertion
                    substitution          // substitution
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    // ------------------------------------------------------------------
    // Insights
    // ------------------------------------------------------------------

    /** One analytical line for the statistics card; rendered EN/AR. */
    data class Insight(
        val type: Type,
        /** Category the insight is about, when applicable. */
        val category: GuideCategory?,
        /** Signal count / unrated sources count backing the line. */
        val count: Int
    )

    enum class Type {
        /** "The category you engage with most: X (N signals)". */
        TOP_CATEGORY,
        /** "N sources inside it are still unrated — try them". */
        UNRATED_IN_TOP_CATEGORY
    }

    /** Insights for the user's snapshot; empty while they have no signals. */
    fun insights(signals: GuideSignalsData): List<Insight> {
        if (signals.isEmpty()) return emptyList()
        val byId = WireGuideCatalog.entries.associateBy { it.id }
        val affinity = categoryAffinity(signals, byId)
        val top = affinity.entries.maxWithOrNull(
            compareBy<Map.Entry<GuideCategory, Int>> { it.value }
                .thenBy { it.key.ordinal }
        ) ?: return emptyList()

        val result = mutableListOf(Insight(Type.TOP_CATEGORY, top.key, top.value))
        val unrated = WireGuideCatalog.entries.count {
            it.category == top.key && it.id !in signals.ratings
        }
        if (unrated > 0) {
            result += Insight(Type.UNRATED_IN_TOP_CATEGORY, top.key, unrated)
        }
        return result
    }
}
