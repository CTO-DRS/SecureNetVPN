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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.securenet.vpn.R
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.stats.DataQuotaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v1.8.24 — the monthly data-quota sheet («حصة البيانات الشهرية»),
 * opened from Settings → Limits & protection. Programmatic UI mirroring
 * the other settings sheets (ScrollView > LinearLayout, host zeroed in
 * [onDestroyView]).
 *
 * Five radio presets (Off / 1 / 5 / 10 / 50 GB per month) drive the pure
 * [DataQuotaEngine]; a live month-to-date line is computed off the main
 * thread from the same encrypted log the dashboard uses, so the user
 * picks a cap while looking at the real number it will guard. Saving
 * persists the cap and RESETS the alert-ladder marker — a new cap must
 * speak with a clean slate, and the next crossing alerts again.
 */
class QuotaDialogFragment : BottomSheetDialogFragment() {

    private var host: LinearLayout? = null
    private var usageText: TextView? = null
    private var presetGroup: RadioGroup? = null

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

        content.addView(title(getString(R.string.quota_sheet_title)))
        content.addView(subtitle(getString(R.string.quota_sheet_sub)))

        content.addView(sectionLabel(getString(R.string.quota_presets_section)))
        presetGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.VERTICAL
        }
        val current = DataQuotaEngine.Preset.fromBytes(prefs.dataQuotaBytes)
        DataQuotaEngine.Preset.entries.forEach { preset ->
            presetGroup!!.addView(
                optionRow(
                    getString(presetLabelRes(preset)),
                    getString(presetDescRes(preset)),
                    current == preset
                )
            )
        }
        content.addView(presetGroup)

        content.addView(sectionLabel(getString(R.string.quota_usage_section)))
        usageText = TextView(requireContext()).apply {
            text = getString(R.string.quota_usage_loading)
            setTextColor(resolveColor(android.R.attr.textColorPrimary))
            textSize = 12.5f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundBackground()
        }
        content.addView(usageText)

        content.addView(saveButton(prefs))
        loadMonthUsage()
    }

    /** Real month-to-date total, computed off the main thread. */
    private fun loadMonthUsage() {
        val container =
            (requireActivity().application as AppProvider).provideContainer()
        viewLifecycleOwner.lifecycleScope.launch {
            val used = withContext(Dispatchers.IO) {
                runCatching {
                    val timeline = com.securenet.vpn.data.local.HistoryParser().parse(
                        container.connectionLogger.readAllLines()
                    )
                    val nowUtc = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                    com.securenet.vpn.stats.MonthlyStatsEngine.compute(
                        usage = timeline.usage,
                        sessions = timeline.sessions,
                        year = nowUtc.year,
                        month = nowUtc.monthValue,
                        nowUtc = nowUtc
                    ).totalBytes
                }.getOrDefault(0L)
            }
            usageText?.text = getString(
                R.string.quota_usage_now,
                com.securenet.vpn.stats.MonthlyStatsEngine.formatBytes(used)
            )
        }
    }

    // ------------------------------------------------------------------
    // Selection + persistence
    // ------------------------------------------------------------------

    private fun presetLabelRes(preset: DataQuotaEngine.Preset): Int =
        when (preset) {
            DataQuotaEngine.Preset.OFF -> R.string.quota_off
            else -> DataQuotaEngine.presetLabelRes(preset.bytes)
        }

    private fun presetDescRes(preset: DataQuotaEngine.Preset): Int = when (preset) {
        DataQuotaEngine.Preset.OFF -> R.string.quota_off_desc
        DataQuotaEngine.Preset.GB1 -> R.string.quota_preset_desc
        DataQuotaEngine.Preset.GB5 -> R.string.quota_preset_desc
        DataQuotaEngine.Preset.GB10 -> R.string.quota_preset_desc
        DataQuotaEngine.Preset.GB50 -> R.string.quota_preset_desc
    }

    private fun selectedPreset(): DataQuotaEngine.Preset {
        val group = presetGroup ?: return DataQuotaEngine.Preset.OFF
        val index = group.indexOfChild(group.findViewById<View>(group.checkedRadioButtonId))
        return DataQuotaEngine.Preset.entries[index.coerceIn(0, DataQuotaEngine.Preset.entries.size - 1)]
    }

    private fun save(prefs: SecurePrefs) {
        prefs.dataQuotaBytes = selectedPreset().bytes
        // A new cap re-arms the alert ladder from clean state: the next
        // threshold crossing speaks, whatever the previous cap reported.
        prefs.dataQuotaLevel = DataQuotaEngine.Level.NONE.stored
        Toast.makeText(requireContext(), R.string.quota_saved, Toast.LENGTH_SHORT).show()
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
        text = getString(R.string.quota_save)
        textSize = 13f
        setOnClickListener { save(prefs) }
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
        usageText = null
        presetGroup = null
        super.onDestroyView()
    }
}
