package com.securenet.vpn.ui.settings

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.securenet.vpn.R
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.service.NotifStyle
import com.securenet.vpn.service.NotifText
import com.securenet.vpn.service.SecureNetVpnService
import com.securenet.vpn.service.VpnNotificationManager

/**
 * v1.8.23 — «الإشعار في يدك»: the notification style + lockscreen
 * privacy sheet, opened from Settings → Notifications. Programmatic UI
 * mirroring the other settings sheets (ScrollView > LinearLayout, host
 * zeroed in [onDestroyView]).
 *
 * Two radio groups drive the pure [NotifStyle] policy:
 *  - style: DETAILED / COMPACT / MINIMAL (how much the shade shows);
 *  - lockscreen: HIDE_CONTENT / SHOW_ALL / HIDE_ALL.
 *
 * A live preview line is rebuilt from the pure [NotifText] engine with
 * sample session numbers, so the user sees exactly what each style
 * renders before applying. Apply persists to [SecurePrefs] and, while a
 * session is live, fires [VpnNotificationManager.ACTION_REFRESH] so the
 * posted notification repaints instantly.
 */
class NotifStyleDialogFragment : BottomSheetDialogFragment() {

    private var host: LinearLayout? = null
    private var previewText: TextView? = null
    private var previewLock: TextView? = null
    private var styleGroup: RadioGroup? = null
    private var lockGroup: RadioGroup? = null

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
        val prefs = prefs()

        content.addView(title(getString(R.string.notif_sheet_title)))
        content.addView(subtitle(getString(R.string.notif_sheet_sub)))

        content.addView(sectionLabel(getString(R.string.notif_style_section)))
        styleGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.VERTICAL
        }
        NotifStyle.Style.entries.forEach { style ->
            styleGroup!!.addView(
                optionRow(
                    getString(NotifStyle.styleLabelRes(style.stored)),
                    getString(styleDescRes(style.stored)),
                    prefs.notifStyle == style.stored
                ) { preview() }
            )
        }
        content.addView(styleGroup)

        content.addView(sectionLabel(getString(R.string.notif_lock_section)))
        lockGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.VERTICAL
        }
        NotifStyle.Lockscreen.entries.forEach { lock ->
            lockGroup!!.addView(
                optionRow(
                    getString(NotifStyle.lockscreenLabelRes(lock.stored)),
                    getString(lockDescRes(lock.stored)),
                    prefs.notifLockscreen == lock.stored
                ) { preview() }
            )
        }
        content.addView(lockGroup)

        content.addView(sectionLabel(getString(R.string.notif_preview_label)))
        previewText = TextView(requireContext()).apply {
            setTextColor(resolveColor(android.R.attr.textColorPrimary))
            textSize = 12.5f
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundBackground()
        }
        content.addView(previewText)
        previewLock = TextView(requireContext()).apply {
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
            textSize = 11.5f
            setPadding(dp(12), dp(4), dp(12), 0)
        }
        content.addView(previewLock)

        content.addView(applyButton(prefs))
        preview()
    }

    // ------------------------------------------------------------------
    // Preview + persistence
    // ------------------------------------------------------------------

    /** Selected style from the radio group (index = entry order, never raw stored ints). */
    private fun selectedStyle(): NotifStyle.Style {
        val group = styleGroup ?: return NotifStyle.Style.DETAILED
        val index = group.indexOfChild(group.findViewById<View>(group.checkedRadioButtonId))
        return NotifStyle.Style.entries[index.coerceIn(0, NotifStyle.Style.entries.size - 1)]
    }

    /** Selected lockscreen policy from the radio group. */
    private fun selectedLock(): NotifStyle.Lockscreen {
        val group = lockGroup ?: return NotifStyle.Lockscreen.HIDE_CONTENT
        val index = group.indexOfChild(group.findViewById<View>(group.checkedRadioButtonId))
        return NotifStyle.Lockscreen.entries[index.coerceIn(0, NotifStyle.Lockscreen.entries.size - 1)]
    }

    /** Sample session numbers — the same ladder the tests pin. */
    private fun preview() {
        val style = selectedStyle()
        val lock = selectedLock()

        val rx = 1_258_291L
        val tx = 348_160L
        val startedAt = System.currentTimeMillis() - 42L * 60_000L

        val body = buildString {
            append("«")
            append(getString(R.string.notif_title_connected, "Amsterdam-3"))
            append("»")
            NotifStyle.compactLine(style, rx, tx)?.let {
                append('\n').append(it)
            }
            if (style == NotifStyle.Style.DETAILED) {
                append('\n').append(
                    NotifText.detailedBody(
                        rxBytes = rx, txBytes = tx,
                        rxSpeed = 3_571_712L, txSpeed = 122_880L,
                        startedAtMillis = startedAt,
                        nowMillis = System.currentTimeMillis(),
                        utcOffsetMillis = 0L,
                        downLabel = getString(R.string.notif_expanded_down),
                        upLabel = getString(R.string.notif_expanded_up),
                        sinceLabel = getString(R.string.notif_expanded_since),
                        avgLabel = getString(R.string.notif_avg_label)
                    )
                )
            }
        }
        previewText?.text = body
        previewLock?.text = getString(
            R.string.notif_preview_lock,
            getString(NotifStyle.lockscreenLabelRes(lock.stored))
        )
    }

    private fun apply(prefs: SecurePrefs) {
        prefs.notifStyle = selectedStyle().stored
        prefs.notifLockscreen = selectedLock().stored

        // v1.8.23 — while a session is live, repaint the posted
        // notification immediately (the service is only started on
        // purpose; never from Settings).
        if (SecureNetVpnService.currentState() != com.securenet.vpn.data.model.ConnectionState.DISCONNECTED) {
            context?.startService(
                Intent(requireContext(), SecureNetVpnService::class.java)
                    .setAction(VpnNotificationManager.ACTION_REFRESH)
            )
        }
        Toast.makeText(requireContext(), R.string.notif_saved, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    // ------------------------------------------------------------------
    // View builders (programmatic, mirroring the other settings sheets)
    // ------------------------------------------------------------------

    private fun prefs(): SecurePrefs =
        (requireActivity().application as AppProvider).provideContainer().securePrefs

    private fun styleDescRes(stored: Int): Int = when (NotifStyle.Style.from(stored)) {
        NotifStyle.Style.DETAILED -> R.string.notif_style_detailed_desc
        NotifStyle.Style.COMPACT -> R.string.notif_style_compact_desc
        NotifStyle.Style.MINIMAL -> R.string.notif_style_minimal_desc
    }

    private fun lockDescRes(stored: Int): Int = when (NotifStyle.Lockscreen.from(stored)) {
        NotifStyle.Lockscreen.HIDE_CONTENT -> R.string.notif_lock_data_desc
        NotifStyle.Lockscreen.SHOW_ALL -> R.string.notif_lock_show_desc
        NotifStyle.Lockscreen.HIDE_ALL -> R.string.notif_lock_hide_desc
    }

    private fun title(text: String): View = TextView(requireContext()).apply {
        this.text = text
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(2))
        contentDescription = text
    }

    private fun subtitle(text: String): View = TextView(requireContext()).apply {
        this.text = text
        setTextColor(resolveColor(android.R.attr.textColorSecondary))
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(10))
    }

    private fun sectionLabel(text: String): View = TextView(requireContext()).apply {
        this.text = text
        setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
        textSize = 12.5f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun optionRow(
        title: String,
        desc: String,
        checked: Boolean,
        onPick: () -> Unit
    ): View = RadioButton(requireContext()).apply {
        this.text = "$title — $desc"
        this.isChecked = checked
        textSize = 12.5f
        setPadding(dp(6), dp(6), dp(6), dp(6))
        setOnClickListener { onPick() }
    }

    private fun applyButton(prefs: SecurePrefs): View = MaterialButton(requireContext()).apply {
        text = getString(R.string.notif_save)
        textSize = 13f
        setOnClickListener { apply(prefs) }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(16), 0, 0) }
    }

    private fun roundBackground(): android.graphics.drawable.Drawable {
        val radius = dp(10).toFloat()
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radius
            // Neutral translucent fill — readable on both glass themes.
            setColor(0x1F888888)
        }
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
        previewText = null
        previewLock = null
        styleGroup = null
        lockGroup = null
        super.onDestroyView()
    }
}
