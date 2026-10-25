package com.securenet.vpn.ui.guide

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.securenet.vpn.R
import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideEntry
import com.securenet.vpn.guide.GuideSignalsData
import com.securenet.vpn.guide.GuideStats
import com.securenet.vpn.guide.GuideStore
import com.securenet.vpn.guide.WireGuideCatalog
import com.securenet.vpn.guide.WireGuideFilter

/**
 * v1.8 — shared renderer for the WireGuard config-sources guide. Both the
 * quick bottom sheet ([com.securenet.vpn.ui.servers.ConfigGuideDialogFragment])
 * and the full-screen [GuideFragment] render through this builder so the
 * presentation stays identical in one place.
 *
 * Cards: an illustrated flow strip (full screen only, via [flowRow]), a
 * step-by-step tutorial per section, source cards with a category icon, and
 * a closing tips card. Section headers collapse/expand on tap.
 *
 * v1.8.1 — every source card gains user signals backed by [GuideStore]:
 * a tappable 5-star rating row, a favorite (♥) toggle on the title row and
 * an editable personal note (dialog) that is also searchable. Star and
 * favorite taps repaint in place; note changes may trigger
 * [onSignalsChanged] so hosts can re-render (e.g. favorites-only view).
 *
 * v1.8.2 — cards of the sources in [WireGuideCatalog.editorsChoice] carry
 * an amber "Editor's choice" reliability badge on the title row; the same
 * shared renderer serves both hosts, so the badge appears everywhere.
 *
 * v1.8.3 — the full-screen host can request a statistics card
 * ([renderSections]' [showStats]) headed by the "most rated sources"
 * ranking ([GuideStats]) plus a bulk-delete action that wipes every signal
 * after a count-spelling confirmation dialog; the host is notified through
 * [onSignalsCleared] so it can re-render and toast.
 */
class GuideUiBuilder(
    private val context: Context,
    private val store: GuideStore,
    private val onOpenUrl: (String) -> Unit,
    private val onCopyUrl: (String) -> Unit,
    private val onOpenFullGuide: (() -> Unit)? = null,
    private val onSignalsChanged: (() -> Unit)? = null,
    private val onSignalsCleared: (() -> Unit)? = null
) {

    /** Category icon (also used as the leading illustration on every card). */
    fun iconFor(category: GuideCategory): Int = when (category) {
        GuideCategory.FREE -> R.drawable.ic_globe
        GuideCategory.PAID -> R.drawable.ic_auto_awesome
        GuideCategory.SELF_HOSTED -> R.drawable.ic_dns
        GuideCategory.ROUTERS -> R.drawable.ic_wifi
        GuideCategory.APPS -> R.drawable.ic_qr_code
    }

    /** Unfiltered browse view: optional "full guide" link, sections, tips.
     *  [showStats] (v1.8.3, full screen only) prepends the guide statistics
     *  card when the user has produced any signals at all. */
    fun renderSections(host: LinearLayout, showStats: Boolean = false) {
        host.removeAllViews()
        onOpenFullGuide?.let { host.addView(openFullGuideLink(it)) }
        if (showStats) {
            statsCard()?.let { host.addView(it) }
        }
        WireGuideCatalog.sections().forEach { (category, entries) ->
            val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            body.addView(stepsCard(category))
            WireGuideFilter.sortWithSignals(entries, store).forEach { body.addView(entryCard(it)) }
            host.addView(sectionHeader(category, entries.size, body))
            host.addView(body)
        }
        host.addView(tipsCard())
    }

    /** Filtered view: flat list of matching entry cards only. */
    fun renderFiltered(host: LinearLayout, entries: List<GuideEntry>) {
        host.removeAllViews()
        WireGuideFilter.sortWithSignals(entries, store).forEach { host.addView(entryCard(it)) }
    }

    /** One illustrated how-to step: icon in a circle + caption below. */
    fun flowStep(iconRes: Int, labelRes: Int): LinearLayout {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        column.addView(ImageView(context).apply {
            setImageResource(iconRes)
            setBackgroundResource(R.drawable.bg_icon_circle)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        })
        column.addView(TextView(context).apply {
            text = context.getString(labelRes)
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
            textSize = 10f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        })
        return column
    }

    /** Small arrow between flow steps; mirrors automatically in RTL. */
    fun flowArrow(): ImageView =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_arrow_right)
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                marginStart = dp(2)
                marginEnd = dp(2)
            }
        }

    // ------------------------------------------------------------------ //

    private fun openFullGuideLink(onOpen: () -> Unit): TextView =
        TextView(context).apply {
            text = getString(R.string.guide_open_full)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onOpen() }
        }

    /** Collapsible section header: title + count + arrow glyph. */
    private fun sectionHeader(
        category: GuideCategory,
        count: Int,
        body: LinearLayout
    ): TextView {
        var expanded = true
        return TextView(context).apply {
            text = headerTitle(category, count, expanded)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(2)
            }
            setOnClickListener {
                expanded = !expanded
                text = headerTitle(category, count, expanded)
                body.visibility = if (expanded) View.VISIBLE else View.GONE
            }
        }
    }

    private fun headerTitle(category: GuideCategory, count: Int, expanded: Boolean): String =
        getString(sectionTitleRes(category)) + "  ·  " + count + "  " +
            if (expanded) "▾" else "▸"

    private fun sectionTitleRes(category: GuideCategory): Int = when (category) {
        GuideCategory.FREE -> R.string.guide_section_free
        GuideCategory.PAID -> R.string.guide_section_paid
        GuideCategory.SELF_HOSTED -> R.string.guide_section_selfhosted
        GuideCategory.ROUTERS -> R.string.guide_section_routers
        GuideCategory.APPS -> R.string.guide_section_apps
    }

    /** Small numbered tutorial card tailored to the section's category. */
    private fun stepsCard(category: GuideCategory): MaterialCardView {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val steps = context.resources.getStringArray(
                WireGuideCatalog.stepsRes(category)
            )
            steps.forEachIndexed { index, step ->
                addView(TextView(context).apply {
                    text = (index + 1).toString() + " · " + step
                    setTextColor(resolveColor(android.R.attr.textColorSecondary))
                    textSize = 11.5f
                    setLineSpacing(dp(2).toFloat(), 1f)
                    if (index > 0) {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(3) }
                    }
                })
            }
        }
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            radius = dp(10).toFloat()
            alpha = 0.85f
            setContentPadding(0, 0, 0, 0)
            addView(body)
        }
    }

    /**
     * Source card: leading category icon + name (+ ♥ favorite toggle),
     * link, description, editorial note, star rating row, personal note
     * row and actions (open / copy / note).
     */
    private fun entryCard(entry: GuideEntry): MaterialCardView {
        // v1.8.1 — personal note row (hidden until the user saves one)
        val noteRow = TextView(context).apply {
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            textSize = 11.5f
            setTypeface(typeface, Typeface.ITALIC)
            setLineSpacing(dp(2).toFloat(), 1f)
            visibility = View.GONE
            setOnClickListener { showNoteDialog(entry, this) }
        }
        refreshNoteRow(noteRow, entry)

        val nameRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = entry.name
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            // v1.8.2 — reliability badge on the curated shortlist.
            if (WireGuideCatalog.isEditorsChoice(entry.id)) {
                addView(TextView(context).apply {
                    text = "★ " + getString(R.string.guide_editors_choice)
                    setTextColor(STAR_ON)
                    textSize = 9f
                    maxLines = 1
                    setBackgroundResource(R.drawable.bg_editors_badge)
                    setPadding(dp(7), dp(3), dp(7), dp(3))
                    contentDescription =
                        getString(R.string.guide_editors_choice_cd)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(2) }
                })
            }
            addView(favoriteToggle(entry))
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameRow)
            addView(TextView(context).apply {
                text = entry.url
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
            })
            addView(TextView(context).apply {
                text = getString(entry.descriptionRes)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                textSize = 12.5f
                setLineSpacing(dp(2).toFloat(), 1f)
            })
            entry.noteRes?.let { noteRes ->
                addView(TextView(context).apply {
                    text = getString(noteRes)
                    setTextColor(resolveColor(android.R.attr.textColorSecondary))
                    textSize = 11f
                    setTypeface(typeface, Typeface.ITALIC)
                })
            }
            addView(starRow(entry))
            addView(noteRow)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }

                addView(button(R.string.guide_open, filled = true) { onOpenUrl(entry.url) })
                addView(button(R.string.guide_copy, filled = false) { onCopyUrl(entry.url) })
                addView(button(noteButtonLabel(entry), filled = false) {
                    showNoteDialog(entry, noteRow)
                })
            })
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(ImageView(context).apply {
                setImageResource(iconFor(entry.category))
                setBackgroundResource(R.drawable.bg_icon_circle)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                    marginEnd = dp(10)
                    topMargin = dp(2)
                }
            })
            addView(textColumn)
        }

        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            radius = dp(12).toFloat()
            setContentPadding(0, 0, 0, 0)
            addView(body)
        }
    }

    private fun button(labelRes: Int, filled: Boolean, onClick: () -> Unit) =
        com.google.android.material.button.MaterialButton(
            context, null,
            if (filled) com.google.android.material.R.attr.materialButtonStyle
            else com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(labelRes)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(
                0, dp(40), 1f
            ).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            setOnClickListener { onClick() }
        }

    // ------------------------------------------------------------------
    // v1.8.1 — user signals: favorite toggle, star rating, personal note
    // ------------------------------------------------------------------

    private fun noteButtonLabel(entry: GuideEntry): Int =
        if (store.note(entry.id) != null) R.string.guide_note_edit
        else R.string.guide_note_add

    /** Heart bookmark on the card's title row; toggles in place. */
    private fun favoriteToggle(entry: GuideEntry): TextView = TextView(context).apply {
        textSize = 18f
        setPadding(dp(8), dp(2), dp(2), dp(2))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        fun paint() {
            val fav = store.isFavorite(entry.id)
            text = if (fav) "♥" else "♡"
            setTextColor(if (fav) FAV_ON else STAR_OFF)
            contentDescription = getString(
                if (fav) R.string.guide_fav_cd_on else R.string.guide_fav_cd_off
            )
        }
        paint()
        setOnClickListener {
            store.setFavorite(entry.id, !store.isFavorite(entry.id))
            paint()
            onSignalsChanged?.invoke()
        }
    }

    /**
     * Tappable 1–5 star rating row. Forced LTR so stars always read 1→5,
     * matching the numeric rating in every locale. Tapping the currently
     * top-rated star clears the rating.
     */
    private fun starRow(entry: GuideEntry): LinearLayout {
        val stars = mutableListOf<TextView>()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        for (value in 1..5) {
            val star = TextView(context).apply {
                text = "★"
                textSize = 16f
                setPadding(0, 0, dp(4), 0)
                contentDescription = context.getString(R.string.guide_star_cd, value)
                setOnClickListener {
                    val current = store.rating(entry.id)
                    store.setRating(entry.id, if (current == value) 0 else value)
                    paintStars(stars, store.rating(entry.id))
                }
            }
            stars.add(star)
            row.addView(star)
        }
        paintStars(stars, store.rating(entry.id))
        return row
    }

    private fun paintStars(stars: List<TextView>, rating: Int) {
        stars.forEachIndexed { index, star ->
            star.setTextColor(if (index < rating) STAR_ON else STAR_OFF)
            star.alpha = if (index < rating) 1f else 0.55f
        }
    }

    private fun refreshNoteRow(row: TextView, entry: GuideEntry) {
        val note = store.note(entry.id)
        if (note == null) {
            row.visibility = View.GONE
        } else {
            row.text = getString(R.string.guide_user_note_prefix) + " " + note
            row.visibility = View.VISIBLE
        }
    }

    /** Add/edit/remove the personal note for [entry] via a material dialog. */
    private fun showNoteDialog(entry: GuideEntry, row: TextView) {
        val input = EditText(context).apply {
            setText(store.note(entry.id))
            hint = getString(R.string.guide_note_hint)
            setSingleLine(false)
            minLines = 2
            maxLines = 4
        }
        val container = FrameLayout(context).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.guide_note_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.guide_note_save) { _, _ ->
                store.setNote(entry.id, input.text?.toString())
                refreshNoteRow(row, entry)
                onSignalsChanged?.invoke()
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (store.note(entry.id) != null) {
            dialog.setNeutralButton(R.string.guide_note_remove) { _, _ ->
                store.setNote(entry.id, null)
                refreshNoteRow(row, entry)
                onSignalsChanged?.invoke()
            }
        }
        dialog.show()
    }

    /** Closing card: practical safety/troubleshooting tips. */
    private fun tipsCard(): MaterialCardView {
        val tips = context.resources.getStringArray(R.array.guide_tips)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(TextView(context).apply {
                text = getString(R.string.guide_tips_title)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            })
            tips.forEach { tip ->
                addView(TextView(context).apply {
                    text = "• " + tip
                    setTextColor(resolveColor(android.R.attr.textColorSecondary))
                    textSize = 12f
                    setLineSpacing(dp(2).toFloat(), 1f)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(4) }
                })
            }
        }
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(8)
            }
            radius = dp(12).toFloat()
            setContentPadding(0, 0, 0, 0)
            addView(body)
        }
    }

    // ------------------------------------------------------------------
    // v1.8.3 — statistics card: most rated sources + bulk delete
    // ------------------------------------------------------------------

    /**
     * The guide statistics card — headline summary (rated / favorites /
     * notes), the star distribution and the "most rated sources" ranking.
     * Null while the user has no signals: an empty stats panel would only
     * be noise, and the first rating immediately brings the card to life.
     */
    private fun statsCard(): MaterialCardView? {
        val signals = GuideSignalsData.snapshot(store)
        if (signals.isEmpty()) return null
        val stats = GuideStats.compute(signals, WireGuideCatalog.entries.size)

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(TextView(context).apply {
                text = getString(R.string.guide_stats_title)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = getString(
                    R.string.guide_stats_summary,
                    stats.ratedCount, stats.totalSources,
                    String.format(java.util.Locale.US, "%.1f", stats.averageStars),
                    stats.favoritesCount, stats.notesCount
                )
                setTextColor(resolveColor(android.R.attr.textColorSecondary))
                textSize = 12f
                setLineSpacing(dp(2).toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
            if (stats.ratedCount > 0) {
                addView(TextView(context).apply {
                    text = getString(
                        R.string.guide_stats_distribution,
                        stats.distribution[5] ?: 0, stats.distribution[4] ?: 0,
                        stats.distribution[3] ?: 0, stats.distribution[2] ?: 0,
                        stats.distribution[1] ?: 0
                    )
                    setTextColor(resolveColor(android.R.attr.textColorSecondary))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(3) }
                })
            }
            if (stats.mostRated.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = getString(R.string.guide_stats_top)
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                    textSize = 12.5f
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                })
                stats.mostRated.forEachIndexed { index, ranked ->
                    addView(mostRatedRow(index + 1, ranked))
                }
            }
            addView(button(R.string.guide_stats_clear, filled = false) { confirmClearAll() }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(36)
                ).apply {
                    topMargin = dp(8)
                    marginEnd = dp(4)
                }
            })
        }
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            radius = dp(12).toFloat()
            setContentPadding(0, 0, 0, 0)
            addView(body)
        }
    }

    /** One ranked row: `N.` + name (+ ♥) + stars, stars forced LTR. */
    private fun mostRatedRow(rank: Int, ranked: GuideStats.Ranked): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        row.addView(TextView(context).apply {
            text = "$rank."
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        })
        row.addView(TextView(context).apply {
            text = if (ranked.favorite) "\u2665 " + ranked.name else ranked.name
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            textSize = 12.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        })
        row.addView(TextView(context).apply {
            text = "\u2605".repeat(ranked.stars)
            setTextColor(STAR_ON)
            textSize = 12f
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
        })
        return row
    }

    /**
     * Bulk delete with confirmation: the dialog spells out the exact counts
     * (ratings / favorites / notes) and the action is irreversible. On
     * confirm the store is wiped and [onSignalsCleared] lets the host
     * re-render (the stats card disappears) and toast.
     */
    private fun confirmClearAll() {
        val signals = GuideSignalsData.snapshot(store)
        if (signals.isEmpty()) return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.guide_stats_clear_title)
            .setMessage(
                getString(
                    R.string.guide_stats_clear_message,
                    signals.ratings.size, signals.favorites.size, signals.notes.size
                )
            )
            .setPositiveButton(R.string.guide_stats_clear_action) { _, _ ->
                store.clearAll()
                onSignalsCleared?.invoke()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------ //

    private fun getString(res: Int): String = context.getString(res)

    private fun getString(res: Int, vararg args: Any): String =
        context.getString(res, *args)

    private fun resolveColor(attr: Int): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        /** Filled-star / favorite tints and the dim empty-star tint. */
        val STAR_ON: Int = Color.parseColor("#FFB300")
        val FAV_ON: Int = Color.parseColor("#FF5C72")
        val STAR_OFF: Int = Color.parseColor("#9AA0A6")
    }
}
