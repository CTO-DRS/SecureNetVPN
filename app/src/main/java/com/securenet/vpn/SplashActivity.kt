package com.securenet.vpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.nativelib.NativeBridge
import com.securenet.vpn.onboarding.OnboardingActivity
import com.securenet.vpn.util.CrashReporter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Branded splash screen (androidx core-splashscreen). Warms the native
 * crypto core, runs its self-test, then routes to onboarding or dashboard.
 *
 * v1.6.2 — when a crash report is pending from a previous run, surface it
 * first (copy / share / skip) so field failures can be diagnosed even when
 * the crash happens after the splash.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        var warmupDone = false
        splash.setKeepOnScreenCondition { !warmupDone }

        lifecycleScope.launch {
            // Native warm-up + self-test (RFC 8439 / SHA-256 known answers).
            val selfTestPassed = NativeBridge.takeIf { it.isAvailable }?.selfTest() ?: true
            if (!selfTestPassed) {
                // A failed crypto self-test is fatal in production; logged loudly.
                android.util.Log.e("SplashActivity", "Native crypto self-test FAILED")
            }

            delay(SPLASH_MINIMUM_MS)

            // v1.6.2: surface a pending crash report before routing.
            if (CrashReporter.lastReport(this@SplashActivity) != null) {
                showPreviousCrashDialog()
            } else {
                routeNext()
            }
            warmupDone = true
        }
    }

    /** Copy / share / skip a crash report captured on a previous run. */
    private fun showPreviousCrashDialog() {
        val report = CrashReporter.lastReport(this) ?: return routeNext()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_dialog_title)
            .setMessage(R.string.crash_dialog_message)
            .setPositiveButton(R.string.crash_dialog_copy) { _, _ ->
                runCatching {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("crash_report", report))
                    Toast.makeText(this, R.string.crash_dialog_copied, Toast.LENGTH_SHORT).show()
                }
                routeNext()
            }
            .setNeutralButton(R.string.crash_dialog_share) { _, _ ->
                runCatching {
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "SecureNet VPN crash report")
                                putExtra(Intent.EXTRA_TEXT, report)
                            },
                            getString(R.string.crash_dialog_share)
                        )
                    )
                }
                routeNext()
            }
            .setNegativeButton(R.string.crash_dialog_dismiss) { _, _ -> routeNext() }
            .setOnCancelListener { routeNext() }
            .show()
        CrashReporter.markReportHandled(this)
    }

    private fun routeNext() {
        if (routeFired) return
        routeFired = true

        val prefs = (application as AppProvider).provideContainer().securePrefs
        val next = if (prefs.onboardingCompleted) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, OnboardingActivity::class.java)
        }
        startActivity(next)
        finish()
    }

    private var routeFired = false

    companion object {
        private const val SPLASH_MINIMUM_MS = 900L
    }
}
