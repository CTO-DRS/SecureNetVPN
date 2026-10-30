package com.securenet.vpn.ui.settings

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.securenet.vpn.R

/**
 * v1.8.18 — 🛡️ "Privacy policy" sheet (سياسة الخصوصية), opened from
 * Settings → Support & developer. The policy states what the app actually
 * does: everything runs on the device, nothing is collected or uploaded,
 * locally stored preferences never leave the phone, and permissions exist
 * only to serve their feature — closing with a pointer to the developer's
 * contact channels. Programmatic UI mirroring the other settings sheets;
 * the full text lives in localized strings (privacy_body) so it ships in
 * EN and AR.
 */
class PrivacyDialogFragment : BottomSheetDialogFragment() {

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
        content.addView(updatedLine())
        content.addView(body())
        content.addView(contactHint())
    }

    private fun title(): View = TextView(requireContext()).apply {
        text = getString(R.string.privacy_title)
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(2))
        contentDescription = getString(R.string.privacy_title)
    }

    private fun updatedLine(): View = TextView(requireContext()).apply {
        text = getString(R.string.privacy_updated, com.securenet.vpn.guide.WhatsNew.CURRENT_VERSION)
        setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
        textSize = 11.5f
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(10))
    }

    private fun body(): View = TextView(requireContext()).apply {
        text = getString(R.string.privacy_body)
        setTextColor(resolveColor(android.R.attr.textColorSecondary))
        textSize = 12.5f
        setLineSpacing(dp(3).toFloat(), 1f)
        setTextIsSelectable(true)
    }

    private fun contactHint(): View = TextView(requireContext()).apply {
        text = getString(R.string.privacy_contact_hint)
        setTextColor(resolveColor(android.R.attr.textColorSecondary))
        textSize = 11.5f
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, 0)
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
