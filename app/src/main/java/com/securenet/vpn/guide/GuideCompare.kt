package com.securenet.vpn.guide

/**
 * v1.8.13 — pure engine behind the side-by-side comparison (🔬 "مقارنة
 * جنباً إلى جنب"). Picking between two WireGuard sources usually means
 * hopping between two detail pages and remembering the differences; this
 * answers it in one sheet: [compare] freezes both entries plus every
 * comparable fact the catalog and the user's signals can offer, and
 * [candidates] ranks the sensible second sources to compare against.
 *
 * Everything is computed from catalog data ([GuideEntry], Editor's-choice
 * badge, curated keywords) and the user's own signals ([GuideStore]) —
 * no invented scores, no network. The shared-keyword row reuses
 * [GuideSmart.sharedKeywords] so "why do these two feel similar" is
 * answered with the same vocabulary the smart layer already uses
 * (Sweden links Mullvad/AzireVPN/OVPN, mesh links Tailscale/NetBird/…).
 *
 * Pure Kotlin over the JVM-testable catalog, so the whole behaviour is
 * unit-tested without any Android dependency.
 */
object GuideCompare {

    /**
     * The frozen, comparable view of two sources. Hosts are resolved once
     * ([GuideDetail.hostOf]) so the sheet can show the trust signal even
     * for long URLs; prices reuse the quick filter's classification
     * ([WireGuideFilter.priceOf]) so the comparison speaks the same
     * free/paid language as the buttons above the list.
     */
    data class Comparison(
        val left: GuideEntry,
        val right: GuideEntry,
        /** True when both live in the same catalog section. */
        val sameCategory: Boolean,
        /** Curated keywords both entries share (lowercased, trimmed). */
        val sharedKeywords: Set<String>,
        val leftHost: String?,
        val rightHost: String?,
        val leftEditorsChoice: Boolean,
        val rightEditorsChoice: Boolean,
        val leftPrice: WireGuideFilter.Price?,
        val rightPrice: WireGuideFilter.Price?
    ) {
        /** User's star ratings (0 when unrated) at comparison time. */
        fun ratingOf(entry: GuideEntry, store: GuideStore): Int =
            store.rating(entry.id)

        /** User's bookmark state at comparison time. */
        fun isFavorite(entry: GuideEntry, store: GuideStore): Boolean =
            store.isFavorite(entry.id)
    }

    /**
     * Compare [a] (the source whose page opened the sheet) with [b].
     * Argument order is preserved — the sheet renders left = [a],
     * right = [b].
     */
    fun compare(a: GuideEntry, b: GuideEntry): Comparison = Comparison(
        left = a,
        right = b,
        sameCategory = a.category == b.category,
        sharedKeywords = GuideSmart.sharedKeywords(a, b),
        leftHost = GuideDetail.hostOf(a),
        rightHost = GuideDetail.hostOf(b),
        leftEditorsChoice = WireGuideCatalog.isEditorsChoice(a.id),
        rightEditorsChoice = WireGuideCatalog.isEditorsChoice(b.id),
        leftPrice = WireGuideFilter.priceOf(a),
        rightPrice = WireGuideFilter.priceOf(b)
    )

    /**
     * The second-source candidates for comparing with [base], most
     * comparable first: same-section sources lead (apples-to-apples),
     * then whichever entries share the most curated keywords with the
     * base (the smart layer's "similar" vocabulary), then the user's own
     * signals (bookmarks → stars → Editor's choice) chained into the
     * same comparator, with the catalog's curated order breaking
     * remaining ties via the stable sort. [base] itself is never a
     * candidate.
     */
    fun candidates(
        base: GuideEntry,
        store: GuideStore,
        catalog: List<GuideEntry> = WireGuideCatalog.entries,
        limit: Int = 15
    ): List<GuideEntry> = catalog
        .filter { it.id != base.id }
        .sortedWith(
            compareByDescending<GuideEntry> { it.category == base.category }
                .thenByDescending { GuideSmart.sharedKeywords(base, it).size }
                .thenByDescending { store.isFavorite(it.id) }
                .thenByDescending { store.rating(it.id) }
                .thenByDescending { WireGuideCatalog.isEditorsChoice(it.id) }
        )
        .take(limit.coerceAtLeast(0))
}
