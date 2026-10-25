package com.securenet.vpn.guide

/**
 * v1.8 — pure search/filter logic for the WireGuard config-sources guide.
 * A query is split into whitespace tokens; every token must appear in the
 * entry's name, URL, curated keywords (EN + Arabic aliases, maintained in
 * [WireGuideCatalog]) or its category's terms. That makes brand names,
 * domains and Arabic words like "مجاني" (free) all work as search terms.
 *
 * v1.8.1 — the haystack also covers the user's personal notes
 * ([filter]'s [notes] map) and results can be restricted to bookmarks
 * ([favorites]); [sortWithSignals] floats favorites and top-rated sources
 * to the top while keeping the catalog order inside ties (stable sort).
 *
 * Pure Kotlin (no Android imports) so the whole behaviour is unit-testable.
 */
object WireGuideFilter {

    /** Words that select an entire category, in EN + AR. */
    fun categoryTerms(category: GuideCategory): List<String> = when (category) {
        GuideCategory.FREE ->
            listOf("free", "مجاني", "مجاناً", "مجانية")
        GuideCategory.PAID ->
            listOf("paid", "premium", "subscription", "مدفوع", "مدفوعون", "اشتراك")
        GuideCategory.SELF_HOSTED ->
            listOf("self", "hosted", "hosting", "vps", "server", "raspberry",
                "استضافة", "ذاتي", "خادم", "سيرفر")
        GuideCategory.ROUTERS ->
            listOf("router", "routers", "firewall", "راوتر", "راوترات", "جدار")
        GuideCategory.APPS ->
            listOf("app", "apps", "client", "clients", "تطبيق", "تطبيقات", "عميل")
    }

    /** Lowercased haystack for one entry: name + URL + keywords + category terms (+ personal note when supplied). */
    private fun haystack(entry: GuideEntry, note: String? = null): String = buildString {
        append(entry.name.lowercase())
        append(' ')
        append(entry.url.lowercase())
        append(' ')
        append(WireGuideCatalog.keywordsOf(entry.id).joinToString(" ") { it.lowercase() })
        append(' ')
        append(categoryTerms(entry.category).joinToString(" ") { it.lowercase() })
        if (!note.isNullOrBlank()) {
            append(' ')
            append(note.lowercase())
        }
    }

    /**
     * True when every whitespace-separated token of [query] hits the entry.
     * [note] is the user's personal note for the entry — when present it is
     * searchable too, so typing "night server" finds the entry the user
     * annotated with that phrase.
     */
    fun matches(entry: GuideEntry, query: String, note: String? = null): Boolean {
        val tokens = query.trim().lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true
        val hay = haystack(entry, note)
        return tokens.all { hay.contains(it) }
    }

    /**
     * Combined filter: restrict to [category] when non-null, to the
     * bookmarked ids in [favorites] when non-null, then keep the entries
     * matching [query] (their personal notes included via [notes]).
     * Order preserved for stable rendering.
     */
    fun filter(
        entries: List<GuideEntry>,
        query: String,
        category: GuideCategory? = null,
        notes: Map<String, String>? = null,
        favorites: Set<String>? = null
    ): List<GuideEntry> = entries.filter { entry ->
        (category == null || entry.category == category) &&
            (favorites == null || entry.id in favorites) &&
            matches(entry, query, notes?.get(entry.id))
    }

    /**
     * v1.8.1 — user-signal ordering: bookmarked sources first, then by
     * star rating (high → low). Kotlin sorts are stable, so within equal
     * signals the catalog's curated order is preserved.
     */
    fun sortWithSignals(entries: List<GuideEntry>, store: GuideStore): List<GuideEntry> =
        entries.sortedWith(
            compareByDescending<GuideEntry> { store.isFavorite(it.id) }
                .thenByDescending { store.rating(it.id) }
        )
}
