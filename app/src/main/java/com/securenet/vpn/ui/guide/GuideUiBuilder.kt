package com.securenet.vpn.ui.guide

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.securenet.vpn.R
import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideEntry
import com.securenet.vpn.guide.WireGuideCatalog

/**
 * v1.8 — shared renderer for the WireGuard config-sources guide. Both the
 * quick bottom sheet ([com.securenet.vpn.ui.servers.ConfigGuideDialogFragment])
 * and the full-screen [GuideFragment] render through this builder so the
 * presentation stays identical in one place.
 *
 * Cards: an illustrated flow strip (full screen only, via [flowRow]), a
 * step-by-step tutorial per section, source cards with a category icon, and
 * a closing tips card. Section headers collapse/expand on tap.
 */
class GuideUiBuilder(
    private val context: Context,
    private val onOpenUrl: (String) -> Unit,
    private val onCopyUrl: (String) -> Unit,
    private val onOpenFullGuide: (() -> Unit)? = null
) {

    /** Category icon (also used as the leading illustration on every card). */
    fun iconFor(category: GuideCategory): Int = when (category) {
        GuideCategory.FREE -> R.drawable.ic_globe
        GuideCategory.PAID -> R.drawable.ic_auto_awesome
        GuideCategory.SELF_HOSTED -> R.drawable.ic_dns
        GuideCategory.ROUTERS -> R.drawable.ic_wifi
        GuideCategory.APPS -> R.drawable.ic_qr_code
    }

    /** Unfiltered browse view: optional "full guide" link, sections, tips. */
    fun renderSections(host: LinearLayout) {
        host.removeAllViews()
        onOpenFullGuide?.let { host.addView(openFullGuideLink(it)) }
        WireGuideCatalog.sections().forEach { (category, entries) ->
            val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            body.addView(stepsCard(category))
            entries.forEach { body.addView(entryCard(it)) }
            host.addView(sectionHeader(category, entries.size, body))
            host.addView(body)
        }
        host.addView(tipsCard())
    }

    /** Filtered view: flat list of matching entry cards only. */
    fun renderFiltered(host: LinearLayout, entries: List<GuideEntry>) {
        host.removeAllViews()
        entries.forEach { host.addView(entryCard(it)) }
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

    /** Source card: leading category icon + name/link/description/note + actions. */
    private fun entryCard(entry: GuideEntry): MaterialCardView {
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = entry.name
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            })
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
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }

                addView(button(R.string.guide_open, filled = true) { onOpenUrl(entry.url) })
                addView(button(R.string.guide_copy, filled = false) { onCopyUrl(entry.url) })
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

    // ------------------------------------------------------------------ //

    private fun getString(res: Int): String = context.getString(res)

    private fun resolveColor(attr: Int): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
