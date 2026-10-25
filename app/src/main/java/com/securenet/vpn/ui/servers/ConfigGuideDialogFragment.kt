package com.securenet.vpn.ui.servers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.securenet.vpn.R
import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.WireGuideCatalog

/**
 * v1.7 — "Where to get WireGuard configs" guide: a scrollable bottom sheet
 * listing free services, paid providers with a config generator, self-hosted
 * server software and official clients — every entry with an Open (browser)
 * and Copy-link action. Sections are built programmatically from the pure
 * [WireGuideCatalog] data so content and presentation stay decoupled.
 */
class ConfigGuideDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_config_guide, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sectionsHost = view.findViewById<LinearLayout>(R.id.guideSections)
        WireGuideCatalog.sections().forEach { (category, entries) ->
            sectionsHost.addView(sectionHeader(category))
            entries.forEach { entry ->
                sectionsHost.addView(entryCard(entry))
            }
        }
    }

    private fun sectionHeader(category: GuideCategory): TextView =
        TextView(requireContext()).apply {
            text = getString(
                when (category) {
                    GuideCategory.FREE -> R.string.guide_section_free
                    GuideCategory.PAID -> R.string.guide_section_paid
                    GuideCategory.SELF_HOSTED -> R.string.guide_section_selfhosted
                    GuideCategory.APPS -> R.string.guide_section_apps
                }
            )
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(2)
            }
        }

    private fun entryCard(entry: com.securenet.vpn.guide.GuideEntry): MaterialCardView {
        val ctx = requireContext()
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))

            addView(TextView(ctx).apply {
                text = entry.name
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(ctx).apply {
                text = entry.url
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                textSize = 11f
                typeface = MONOSPACE
                setTextIsSelectable(true)
            })
            addView(TextView(ctx).apply {
                text = getString(entry.descriptionRes)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                textSize = 12.5f
                setLineSpacing(dp(2).toFloat(), 1f)
            })
            entry.noteRes?.let { noteRes ->
                addView(TextView(ctx).apply {
                    text = getString(noteRes)
                    setTextColor(resolveColor(android.R.attr.textColorSecondary))
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.ITALIC)
                })
            }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }

                addView(MaterialButton(ctx, null,
                    com.google.android.material.R.attr.materialButtonStyle).apply {
                    text = getString(R.string.guide_open)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                        marginEnd = dp(6)
                    }
                    setOnClickListener { openUrl(entry.url) }
                })
                addView(MaterialButton(ctx, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = getString(R.string.guide_copy)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                        marginStart = dp(6)
                    }
                    setOnClickListener { copyUrl(entry.url) }
                })
            })
        }

        return MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            radius = dp(12).toFloat()
            setContentPadding(0, 0, 0, 0)
            addView(body)
        }
    }

    private fun openUrl(url: String) {
        val launched = runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        if (!launched) {
            view?.let { Snackbar.make(it, R.string.guide_no_browser, Snackbar.LENGTH_SHORT).show() }
        }
    }

    private fun copyUrl(url: String) {
        runCatching {
            val clipboard =
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("WireGuard config source", url))
        }
        view?.let { Snackbar.make(it, R.string.guide_link_copied, Snackbar.LENGTH_SHORT).show() }
    }

    private fun resolveColor(attr: Int): Int {
        val value = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        val MONOSPACE = android.graphics.Typeface.MONOSPACE
    }
}
