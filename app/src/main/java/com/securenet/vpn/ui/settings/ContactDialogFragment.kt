package com.securenet.vpn.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.securenet.vpn.R
import com.securenet.vpn.about.AboutInfo

/**
 * v1.8.18 — 📇 "Contact the developer" sheet (تواصل مع المطوّر), opened from
 * Settings → Support & developer. One tappable row per published channel of
 * [AboutInfo] (WhatsApp business chat, Telegram, Instagram, TikTok, X,
 * YouTube, GitHub, website — exactly as on the developer's contact card):
 * tapping a row deep-links into the matching app via ACTION_VIEW, falling
 * back to the browser; if nothing can open the link we say so honestly
 * instead of failing silently. Programmatic UI, mirroring the checker and
 * what's-new sheets — no new XML layouts.
 */
class ContactDialogFragment : BottomSheetDialogFragment() {

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
        content.addView(developerLine())

        AboutInfo.contacts.forEach { contact ->
            content.addView(channelRow(contact))
        }
    }

    private fun title(): View = TextView(requireContext()).apply {
        text = getString(R.string.contact_title)
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(2))
        contentDescription = getString(R.string.contact_title)
    }

    /** Who you are writing to — the persona printed on the contact card. */
    private fun developerLine(): View = TextView(requireContext()).apply {
        text = getString(R.string.contact_dev_name, AboutInfo.DEVELOPER_NAME)
        setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
        textSize = 12.5f
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(10))
    }

    /** One channel row: name + handle + arrow, opening the official URL. */
    private fun channelRow(contact: AboutInfo.Contact): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ripple()
            setPadding(dp(12), dp(11), dp(12), dp(11))
            setOnClickListener { open(contact) }
            contentDescription = contact.name
        }

        val texts = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        texts.addView(TextView(requireContext()).apply {
            text = contact.name
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
        })
        texts.addView(TextView(requireContext()).apply {
            text = contact.handle
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
            textSize = 12f
        })

        row.addView(texts)
        row.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_arrow_right)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        return row
    }

    private fun open(contact: AboutInfo.Contact) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contact.url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(), R.string.contact_open_failed, Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun ripple(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(resolveColor(android.R.attr.textColorSecondary) and 0x0FFFFFFF)
        setStroke(dp(1), resolveColor(android.R.attr.textColorSecondary) and 0x22FFFFFF)
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
