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
 * v1.8.2 — "Editor's choice" entries ([WireGuideCatalog.editorsChoice])
 * are searchable through badge terms ("editor", "موثوق", …) and act as a
 * sort tie-break after the star rating, so the most reliable sources rise
 * to the top of the default ordering and of the new Top-rated chip.
 *
 * v1.8.13 — the mesh tools get their own category terms, and a quick
 * price filter joins the chips above the list: [priceOf] derives each
 * source's price class from its category plus the explicit free/paid
 * keyword markers, and [filterByPrice] keeps one class only — the pure
 * counterpart of the new Free/Paid buttons in [GuideFragment].
 *
 * v1.8.14 — the setup difficulty ([GuideEntry.difficulty]) is searchable
 * in EN + AR (easy/سهل, advanced/متقدم, …), so typing "سهل" from the
 * Arabic list reaches every beginner source across all sections the same
 * way price words already do.
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
        GuideCategory.MESH ->
            listOf("mesh", "overlay", "network", "peers",
                "شبكة", "شبكات", "ميش")
    }

    /**
     * v1.8.13 — the price class of a source for the quick Free/Paid
     * filter above the list. Derived from the category (the FREE and PAID
     * sections are self-describing) plus the explicit price markers in the
     * curated keywords (free/مجاني, paid/مدفوع — v1.8.12), so cross-section
     * entries like the free self-hosted tools and the mesh sources are
     * classified too. Sources with neither signal (e.g. a neutral app)
     * return null and appear in both filter results' complement — the
     * All button shows them, a price button hides them.
     */
    fun priceOf(entry: GuideEntry): Price? {
        val marks = WireGuideCatalog.keywordsOf(entry.id)
            .map { it.lowercase().trim() }.toSet()
        val free = entry.category == GuideCategory.FREE ||
            "free" in marks || "مجاني" in marks
        val paid = entry.category == GuideCategory.PAID ||
            "paid" in marks || "مدفوع" in marks
        return when {
            free -> Price.FREE
            paid -> Price.PAID
            else -> null
        }
    }

    /**
     * v1.8.13 — quick price filter: null keeps everything (the All state),
     * otherwise only sources whose [priceOf] matches survive. Order is
     * preserved for stable rendering.
     */
    fun filterByPrice(entries: List<GuideEntry>, price: Price?): List<GuideEntry> =
        if (price == null) entries
        else entries.filter { priceOf(it) == price }

    /** v1.8.13 — price class used by the quick filter buttons. */
    enum class Price { FREE, PAID }

    /** Lowercased haystack for one entry: name + URL + keywords + category terms (+ personal note when supplied). */
    private fun haystack(entry: GuideEntry, note: String? = null): String = buildString {
        append(entry.name.lowercase())
        append(' ')
        append(entry.url.lowercase())
        append(' ')
        append(WireGuideCatalog.keywordsOf(entry.id).joinToString(" ") { it.lowercase() })
        append(' ')
        append(categoryTerms(entry.category).joinToString(" ") { it.lowercase() })
        // v1.8.2 — "Editor's choice" badge terms are searchable too.
        if (WireGuideCatalog.isEditorsChoice(entry.id)) {
            append(" editor's choice editors ")
            append("اختيار المحرر موثوق موثوقة")
        }
        // v1.8.14 — the setup difficulty is searchable (EN + AR).
        when (entry.difficulty) {
            GuideDifficulty.BEGINNER ->
                append(" beginner easy simple quick مبتدئ سهل بسيط سريع")
            GuideDifficulty.INTERMEDIATE ->
                append(" intermediate medium guided متوسط")
            GuideDifficulty.ADVANCED ->
                append(" advanced expert hard difficult متقدم صعب خبير")
        }
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
     * star rating (high → low). v1.8.2 — "Editor's choice" entries break
     * remaining ties, so the most reliable unrated sources lead the list.
     * Kotlin sorts are stable, so within equal signals the catalog's
     * curated order is preserved.
     */
    fun sortWithSignals(entries: List<GuideEntry>, store: GuideStore): List<GuideEntry> =
        entries.sortedWith(
            compareByDescending<GuideEntry> { store.isFavorite(it.id) }
                .thenByDescending { store.rating(it.id) }
                .thenByDescending { WireGuideCatalog.isEditorsChoice(it.id) }
        )
}
