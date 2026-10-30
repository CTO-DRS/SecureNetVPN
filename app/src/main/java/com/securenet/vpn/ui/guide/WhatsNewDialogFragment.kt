package com.securenet.vpn.ui.guide

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.securenet.vpn.R
import com.securenet.vpn.guide.WhatsNew

/**
 * v1.8.17 — 📊 صفحة «ما الجديد»: the in-app release feed as a bottom
 * sheet, mirroring the checker sheet's programmatic style. Newest release
 * first; every version is a bold header row followed by its headline
 * bullets, so the guide's changelog is readable without leaving the app.
 *
 * Shown automatically once per version by [GuideFragment.maybeShowWhatsNew]
 * and re-openable from the guide tools row — no network, no remote config:
 * the content is the shipped [WhatsNew] snapshot, which is exactly why it
 * can be pinned by JVM tests.
 */
class WhatsNewDialogFragment : BottomSheetDialogFragment() {

    private var host: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scrollBarStyle = ScrollView.SCROLLBARS_INSIDE_INSET
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        host = content
        root.addView(content)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val content = host ?: return

        content.addView(title())

        WhatsNew.releases.forEachIndexed { index, release ->
            if (index > 0) content.addView(divider())
            content.addView(versionHeader(release.version, index == 0))
            if (index == 0) content.addView(badgesRow())
            release.items.forEach { itemRes ->
                content.addView(bullet(getString(itemRes)))
            }
        }

        content.addView(shareButton())
    }

    /**
     * v1.8.18 — capability badges under the current release: a difficulty
     * chip row (what the filter covers) and the checker's languages, so a
     * reader sees the sheet's own feature surface at a glance.
     */
    private fun badgesRow(): View = HorizontalScrollView(requireContext()).apply {
        isHorizontalScrollBarEnabled = false
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(4), 0, dp(2)) }
        addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), dp(2), dp(2))
            WhatsNew.currentBadges.forEach { badgeRes ->
                addView(badge(getString(badgeRes)))
            }
        })
    }

    /** One rounded, primary-tinted pill. */
    private fun badge(text: String): View = TextView(requireContext()).apply {
        this.text = text
        setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
        textSize = 11f
        val padH = dp(10)
        setPadding(padH, dp(5), padH, dp(5))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(
                resolveColor(com.google.android.material.R.attr.colorPrimary) and 0x18FFFFFF
            )
            setStroke(
                dp(1),
                resolveColor(com.google.android.material.R.attr.colorPrimary) and 0x55FFFFFF
            )
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8) }
    }

    /** v1.8.18 — "share this release": version bullets + releases link via the system chooser. */
    private fun shareButton(): View = MaterialButton(requireContext()).apply {
        text = getString(R.string.whatsnew_share)
        textSize = 13f
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(14), 0, 0) }
        setOnClickListener {
            val current = WhatsNew.releases.first()
            val text = WhatsNew.shareText(
                current.version,
                current.items.map { getString(it) }
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(
                Intent.createChooser(intent, getString(R.string.whatsnew_share_chooser))
            )
        }
    }

    private fun title(): View = TextView(requireContext()).apply {
        text = getString(R.string.whatsnew_title)
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(10))
        contentDescription = getString(R.string.whatsnew_cd)
    }

    /** Version header — the current release gets the primary tint + a "now" hint. */
    private fun versionHeader(version: String, current: Boolean): View =
        TextView(requireContext()).apply {
            text = if (current) getString(R.string.whatsnew_version_current, version)
            else getString(R.string.whatsnew_version, version)
            setTextColor(
                if (current) resolveColor(com.google.android.material.R.attr.colorPrimary)
                else resolveColor(android.R.attr.textColorSecondary)
            )
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(2))
        }

    private fun bullet(body: String): View = TextView(requireContext()).apply {
        text = "•  $body"
        setTextColor(resolveColor(android.R.attr.textColorSecondary))
        textSize = 12.5f
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(dp(10), 0, 0, dp(4))
    }

    private fun divider(): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { setMargins(0, dp(6), 0, dp(2)) }
        setBackgroundColor(
            resolveColor(android.R.attr.textColorSecondary) and 0x33FFFFFF
        )
    }

    private fun resolveColor(attr: Int): Int {
        val value = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(value: Int): Int =
        (value * requireContext().resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        host = null
        super.onDestroyView()
    }
}
