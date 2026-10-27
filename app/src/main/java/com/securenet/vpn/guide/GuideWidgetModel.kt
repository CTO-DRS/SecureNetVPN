package com.securenet.vpn.guide

/**
 * v1.8.11 — pure view-model behind the home-screen "Smart picks" widget.
 *
 * The widget is a thin RemoteViews shell: everything it displays is decided
 * here, on the JVM, from the same immutable [GuideSignalsData] snapshot the
 * guide screen uses — [GuideSmart.recommendations] rows (source id, name,
 * reason chip, score) or an explicit empty state when nothing scores. No
 * Android imports, fully unit-testable; the provider only maps rows onto
 * static views and wires tap intents.
 */
object GuideWidgetModel {

    /** One tappable widget row: a recommended source and why it surfaced. */
    data class Row(
        /** Catalog id — the provider routes the tap to this source's detail page. */
        val sourceId: String,
        /** Display name straight from the catalog entry. */
        val name: String,
        /** Why this source surfaced (same enum the picks card uses). */
        val reason: GuideSmart.Reason,
        /** Deterministic score behind the ordering (0 for cold-start picks). */
        val score: Int
    )

    /** What the widget shows: up to [build]'s limit rows, or the hint line. */
    data class Content(
        val rows: List<Row>,
        /**
         * True when nothing can be recommended right now — every entry is
         * either bookmarked, already engaged, or outside the user's pattern.
         * The provider swaps the row list for the "rate sources" hint.
         */
        val empty: Boolean
    )

    /**
     * Builds the widget content for [signals]: exactly the smart picks the
     * guide's "Smart picks for you" card would show, capped at [limit]
     * (widgets are small; three rows fit comfortably). Deterministic —
     * same signals, same rows, so the home screen never flaps.
     */
    fun build(
        signals: GuideSignalsData,
        catalog: List<GuideEntry> = WireGuideCatalog.entries,
        limit: Int = 3
    ): Content {
        val rows = GuideSmart.recommendations(signals, catalog, limit).map { rec ->
            Row(
                sourceId = rec.entry.id,
                name = rec.entry.name,
                reason = rec.reason,
                score = rec.score
            )
        }
        return Content(rows, rows.isEmpty())
    }
}
