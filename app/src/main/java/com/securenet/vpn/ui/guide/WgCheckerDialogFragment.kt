package com.securenet.vpn.ui.guide

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.securenet.vpn.R
import com.securenet.vpn.util.WireGuardConfigChecker

/**
 * v1.8.16 — 🛡️ فاحص إعدادات WireGuard: paste or import a wg-quick style
 * .conf and get a line-anchored list of problems before importing it into
 * SecureNet. Opened from the guide screen's checker button; the analysis
 * itself is the pure [WireGuardConfigChecker] — everything runs locally,
 * nothing is ever uploaded (the sheet says so in plain words).
 *
 * Every reported issue renders as one row: severity icon (❌ / ⚠️ / ℹ️),
 * the line number ("سطر 12 — …") and a localized message naming the exact
 * token that tripped the rule, so the fix is usually obvious: a typo'd key,
 * a missing Address, a port off by one.
 */
class WgCheckerDialogFragment : BottomSheetDialogFragment() {

    private var host: LinearLayout? = null
    private var configInput: EditText? = null
    private var resultsHost: LinearLayout? = null

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val ctx = context ?: return@registerForActivityResult
            if (uri == null) return@registerForActivityResult
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                Toast.makeText(
                    ctx, R.string.wgcheck_import_failed, Toast.LENGTH_SHORT
                ).show()
            } else {
                configInput?.setText(text)
                resultsHost?.removeAllViews()
            }
        }

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

        content.addView(title(R.string.wgcheck_title))
        content.addView(caption(R.string.wgcheck_privacy))

        val input = EditText(requireContext()).apply {
            hint = getString(R.string.wgcheck_hint)
            gravity = Gravity.TOP or Gravity.START
            setSingleLine(false)
            minLines = 7
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        configInput = input
        content.addView(input)

        content.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            addView(button(R.string.wgcheck_import, filled = false) {
                importFile.launch("*/*")
            })
            addView(button(R.string.wgcheck_run, filled = true) { runCheck() })
        })

        val results = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        resultsHost = results
        content.addView(results)
    }

    private fun runCheck() {
        val text = configInput?.text?.toString().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(
                requireContext(), R.string.wgcheck_empty_input, Toast.LENGTH_SHORT
            ).show()
            return
        }
        paintResults(WireGuardConfigChecker.check(text))
    }

    private fun paintResults(report: WireGuardConfigChecker.Report) {
        val results = resultsHost ?: return
        results.removeAllViews()

        val tone = when {
            report.hasErrors -> ERROR_RED
            report.warningCount > 0 -> WARN_AMBER
            else -> OK_GREEN
        }
        results.addView(TextView(requireContext()).apply {
            text = if (report.hasErrors || report.warningCount > 0)
                getString(R.string.wgcheck_result, report.errorCount, report.warningCount)
            else
                getString(R.string.wgcheck_ok)
            setTextColor(tone)
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        })
        results.addView(TextView(requireContext()).apply {
            text = getString(R.string.wgcheck_shape, report.interfaceCount, report.peerCount)
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
            textSize = 11.5f
            setPadding(0, dp(2), 0, dp(4))
        })

        report.issues.forEach { results.addView(issueRow(it)) }
    }

    /** One row: severity icon + "line N — message". */
    private fun issueRow(issue: WireGuardConfigChecker.Issue): View {
        val (icon, tint) = when (issue.severity) {
            WireGuardConfigChecker.Severity.ERROR -> "❌" to ERROR_RED
            WireGuardConfigChecker.Severity.WARNING -> "⚠️" to WARN_AMBER
            WireGuardConfigChecker.Severity.INFO -> "ℹ️" to INFO_BLUE
        }
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(6), 0, dp(2))
            addView(TextView(requireContext()).apply {
                text = icon
                setTextColor(tint)
                textSize = 12f
                setPadding(0, 0, dp(6), 0)
            })
            addView(TextView(requireContext()).apply {
                text = getString(R.string.wgcheck_line, issue.line) + " — " +
                    messageFor(issue)
                setTextColor(resolveColor(android.R.attr.textColorSecondary))
                textSize = 12f
                setLineSpacing(dp(2).toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })
        }
    }

    /** Maps a stable checker code to its localized message. */
    private fun messageFor(issue: WireGuardConfigChecker.Issue): String {
        val res = when (issue.code) {
            "empty_config" -> R.string.wgcheck_msg_empty_config
            "missing_interface" -> R.string.wgcheck_msg_missing_interface
            "multiple_interface" -> R.string.wgcheck_msg_multiple_interface
            "missing_private_key" -> R.string.wgcheck_msg_missing_private_key
            "missing_address" -> R.string.wgcheck_msg_missing_address
            "missing_public_key" -> R.string.wgcheck_msg_missing_public_key
            "missing_allowed_ips" -> R.string.wgcheck_msg_missing_allowed_ips
            "missing_endpoint" -> R.string.wgcheck_msg_missing_endpoint
            "no_peer" -> R.string.wgcheck_msg_no_peer
            "invalid_key" -> R.string.wgcheck_msg_invalid_key
            "invalid_cidr" -> R.string.wgcheck_msg_invalid_cidr
            "invalid_dns" -> R.string.wgcheck_msg_invalid_dns
            "invalid_mtu" -> R.string.wgcheck_msg_invalid_mtu
            "low_mtu" -> R.string.wgcheck_msg_low_mtu
            "invalid_listen_port" -> R.string.wgcheck_msg_invalid_listen_port
            "invalid_keepalive" -> R.string.wgcheck_msg_invalid_keepalive
            "invalid_endpoint" -> R.string.wgcheck_msg_invalid_endpoint
            "unknown_section" -> R.string.wgcheck_msg_unknown_section
            "unknown_key" -> R.string.wgcheck_msg_unknown_key
            "duplicate_key" -> R.string.wgcheck_msg_duplicate_key
            "unparsed_line" -> R.string.wgcheck_msg_unparsed_line
            "stray_line" -> R.string.wgcheck_msg_stray_line
            "full_tunnel" -> R.string.wgcheck_msg_full_tunnel
            else -> return issue.code
        }
        return if (issue.detail.isEmpty()) getString(res)
        else getString(res, issue.detail)
    }

    // ------------------------------------------------------------------
    // Small builders (mirroring the compare sheet's helpers)
    // ------------------------------------------------------------------

    private fun button(labelRes: Int, filled: Boolean, onClick: () -> Unit) =
        MaterialButton(
            requireContext(), null,
            if (filled) com.google.android.material.R.attr.materialButtonStyle
            else com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(labelRes)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            layoutParams = LinearLayout.LayoutParams(
                0, dp(42), 1f
            ).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            setOnClickListener { onClick() }
        }

    private fun title(textRes: Int): View = TextView(requireContext()).apply {
        text = getString(textRes)
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(6))
    }

    private fun caption(textRes: Int): View = TextView(requireContext()).apply {
        text = getString(textRes)
        setTextColor(resolveColor(android.R.attr.textColorSecondary))
        textSize = 11.5f
        setPadding(0, 0, 0, dp(10))
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
        configInput = null
        resultsHost = null
        super.onDestroyView()
    }

    private companion object {
        val ERROR_RED = 0xFFC62828.toInt()
        val WARN_AMBER = 0xFFB26A00.toInt()
        val OK_GREEN = 0xFF2E7D32.toInt()
        val INFO_BLUE = 0xFF1565C0.toInt()
    }
}
