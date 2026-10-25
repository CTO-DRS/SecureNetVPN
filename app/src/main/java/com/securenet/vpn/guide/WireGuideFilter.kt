package com.securenet.vpn.guide

/**
 * v1.8 — pure search/filter logic for the WireGuard config-sources guide.
 * A query is split into whitespace tokens; every token must appear in the
 * entry's name, URL, curated keywords (EN + Arabic aliases, maintained in
 * [WireGuideCatalog]) or its category's terms. That makes brand names,
 * domains and Arabic words like "مجاني" (free) all work as search terms.
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

    /** Lowercased haystack for one entry: name + URL + keywords + category terms. */
    private fun haystack(entry: GuideEntry): String = buildString {
        append(entry.name.lowercase())
        append(' ')
        append(entry.url.lowercase())
        append(' ')
        append(WireGuideCatalog.keywordsOf(entry.id).joinToString(" ") { it.lowercase() })
        append(' ')
        append(categoryTerms(entry.category).joinToString(" ") { it.lowercase() })
    }

    /** True when every whitespace-separated token of [query] hits the entry. */
    fun matches(entry: GuideEntry, query: String): Boolean {
        val tokens = query.trim().lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true
        val hay = haystack(entry)
        return tokens.all { hay.contains(it) }
    }

    /**
     * Combined filter: restrict to [category] when non-null, then keep the
     * entries matching [query]. Order preserved for stable rendering.
     */
    fun filter(
        entries: List<GuideEntry>,
        query: String,
        category: GuideCategory? = null
    ): List<GuideEntry> = entries.filter { entry ->
        (category == null || entry.category == category) && matches(entry, query)
    }
}
