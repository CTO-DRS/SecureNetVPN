package com.securenet.vpn.ui.settings

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
import com.securenet.vpn.security.AppLockPolicy

/**
 * v1.8.24 — the app-lock timing sheet («توقيت القفل»), opened from
 * Settings → Limits & protection → Lock timing. Programmatic UI mirroring
 * the other settings sheets (ScrollView > LinearLayout, host zeroed in
 * [onDestroyView]).
 *
 * Three pure [AppLockPolicy.Timeout] choices — lock immediately after
 * leaving the app, or allow a 1 / 5 minute grace window. The choice is
 * the grace every future lock decision weighs; the switch itself lives
 * on the Settings row.
 */
class AppLockDialogFragment : BottomSheetDialogFragment() {

    private var host: LinearLayout? = null
    private var timeoutGroup: RadioGroup? = null

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

        content.addView(title(getString(R.string.applock_sheet_title)))
        content.addView(subtitle(getString(R.string.applock_sheet_sub)))

        content.addView(sectionLabel(getString(R.string.applock_timeout_section)))
        timeoutGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.VERTICAL
        }
        AppLockPolicy.Timeout.entries.forEach { timeout ->
            timeoutGroup!!.addView(
                optionRow(
                    getString(AppLockPolicy.timeoutLabelRes(timeout.stored)),
                    getString(timeoutDescRes(timeout)),
                    prefs.appLockTimeout == timeout.stored
                )
            )
        }
        content.addView(timeoutGroup)

        content.addView(saveButton(prefs))
    }

    // ------------------------------------------------------------------
    // Selection + persistence
    // ------------------------------------------------------------------

    private fun timeoutDescRes(timeout: AppLockPolicy.Timeout): Int = when (timeout) {
        AppLockPolicy.Timeout.IMMEDIATE -> R.string.applock_timeout_immediate_desc
        AppLockPolicy.Timeout.ONE_MIN -> R.string.applock_timeout_1min_desc
        AppLockPolicy.Timeout.FIVE_MIN -> R.string.applock_timeout_5min_desc
    }

    private fun selectedTimeout(): AppLockPolicy.Timeout {
        val group = timeoutGroup ?: return AppLockPolicy.Timeout.IMMEDIATE
        val index = group.indexOfChild(group.findViewById<View>(group.checkedRadioButtonId))
        return AppLockPolicy.Timeout.entries[index.coerceIn(0, AppLockPolicy.Timeout.entries.size - 1)]
    }

    private fun save(prefs: SecurePrefs) {
        prefs.appLockTimeout = selectedTimeout().stored
        Toast.makeText(requireContext(), R.string.applock_saved, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    // ------------------------------------------------------------------
    // View builders (mirroring the other settings sheets)
    // ------------------------------------------------------------------

    private fun prefs(): SecurePrefs =
        (requireActivity().application as AppProvider).provideContainer().securePrefs

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

    private fun optionRow(label: String, desc: String, checked: Boolean): View =
        RadioButton(requireContext()).apply {
            text = "$label — $desc"
            isChecked = checked
            textSize = 12.5f
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

    private fun saveButton(prefs: SecurePrefs): View = MaterialButton(requireContext()).apply {
        text = getString(R.string.applock_save)
        textSize = 13f
        setOnClickListener { save(prefs) }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(16), 0, 0) }
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
        timeoutGroup = null
        super.onDestroyView()
    }
}
