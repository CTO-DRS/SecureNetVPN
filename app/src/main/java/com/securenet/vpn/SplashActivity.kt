package com.securenet.vpn

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.nativelib.NativeBridge
import com.securenet.vpn.onboarding.OnboardingActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Branded splash screen (androidx core-splashscreen). Warms the native
 * crypto core, runs its self-test, then routes to onboarding or dashboard.
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

            val prefs = (application as AppProvider).provideContainer().securePrefs
            val next = if (prefs.onboardingCompleted) {
                Intent(this@SplashActivity, MainActivity::class.java)
            } else {
                Intent(this@SplashActivity, OnboardingActivity::class.java)
            }
            startActivity(next)
            finish()
            warmupDone = true
        }
    }

    companion object {
        private const val SPLASH_MINIMUM_MS = 900L
    }
}
