package com.securenet.vpn.guide

/**
 * v1.8.7 — pure model + helpers behind the per-source detail page.
 *
 * Tapping any source card now opens a full detail screen. Everything the
 * screen needs beyond the plain [GuideEntry] is computed here so it stays
 * unit-testable on the JVM: the merged view of catalog data + user signals
 * ([facts]), the "more in this category" suggestion list ([related]) and
 * the share text ([shareText]).
 *
 * Ordering reuses [WireGuideFilter.sortWithSignals] (favorites → stars →
 * Editor's choice → catalog order), so a sibling the user already rated or
 * bookmarked floats to the top of the related list — consistent with the
 * default ordering of the guide itself.
 */
object GuideDetail {

    /** Snapshot of one entry's catalog data merged with the user's signals. */
    data class Facts(
        val entry: GuideEntry,
        /** 0 (unrated) or 1..5. */
        val rating: Int,
        val favorite: Boolean,
        /** The saved personal note, or null. */
        val note: String?,
        /** True when the entry carries the "Editor's choice" reliability badge. */
        val editorsChoice: Boolean
    ) {
        /** True when the user has produced any signal for this entry. */
        val hasSignals: Boolean get() = rating > 0 || favorite || note != null
    }

    /** Merged catalog + signals view for [entry]. */
    fun facts(entry: GuideEntry, store: GuideStore): Facts = Facts(
        entry = entry,
        rating = store.rating(entry.id),
        favorite = store.isFavorite(entry.id),
        note = store.note(entry.id),
        editorsChoice = WireGuideCatalog.isEditorsChoice(entry.id)
    )

    /**
     * "More in this category": same-category siblings excluding [entry],
     * user signals first (favorites → stars → Editor's choice), stable
     * catalog order inside ties, capped at [limit].
     */
    fun related(
        entry: GuideEntry,
        store: GuideStore,
        catalog: List<GuideEntry> = WireGuideCatalog.entries,
        limit: Int = 5
    ): List<GuideEntry> =
        WireGuideFilter.sortWithSignals(
            catalog.filter { it.category == entry.category && it.id != entry.id },
            store
        ).take(limit)

    /** One-line share text: "name — url". */
    fun shareText(entry: GuideEntry): String = "${entry.name} — ${entry.url}"

    /**
     * v1.8.8 — the bare official host of [entry]'s URL ("nordvpn.com",
     * "play.google.com"), shown under the QR so the destination domain is
     * readable at a glance — a small trust signal when the code itself is
     * not human-scannable. Strips the "www." prefix; null when the URL
     * does not parse (never happens for catalog entries — enforced by test).
     */
    fun hostOf(entry: GuideEntry): String? = hostOf(entry.url)

    /** Bare host of [url], "www." stripped and lowercased, or null. */
    fun hostOf(url: String): String? = try {
        java.net.URI(url).host?.lowercase()?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}
