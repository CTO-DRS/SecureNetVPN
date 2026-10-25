package com.securenet.vpn

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.util.CrashReporter
import com.securenet.vpn.work.WorkScheduler

/**
 * Application entry point. Owns the manual dependency graph (AppContainer),
 * applies app-wide configuration (Material You dynamic colors, persisted
 * theme mode, periodic WorkManager jobs) and exposes the container to
 * Services through the [AppProvider] contract.
 *
 * v1.6.1 — startup is crash-proof: the global crash reporter is installed
 * before anything else, and every post-container initialisation step is
 * individually guarded so an OEM-specific failure can never produce a
 * launch crash loop again.
 */
class SecureNetApp : Application(), AppProvider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Must be the very first line: from here on any uncaught exception
        // is captured to filesDir/crash_reports/last_crash.txt.
        CrashReporter.install(this)

        container = AppContainer(this)

        // Guarded: theme/prefs problems degrade to the system default look
        // instead of killing the process (fixes the v1.6.0 keystore crash loop).
        runCatching { DynamicColors.applyToActivitiesIfAvailable(this) }
            .onFailure { android.util.Log.e(TAG, "DynamicColors failed", it) }

        runCatching { applyPersistedTheme() }
            .onFailure { android.util.Log.e(TAG, "applyPersistedTheme failed", it) }

        // Periodic connection-log flush + daily usage aggregation.
        runCatching { WorkScheduler.scheduleAll(this) }
            .onFailure { android.util.Log.e(TAG, "WorkScheduler.scheduleAll failed", it) }

        // v1.3: re-arm the connection-schedule tick when a schedule is set.
        runCatching { com.securenet.vpn.work.ScheduleWorker.syncFromPrefs(this) }
            .onFailure { android.util.Log.e(TAG, "ScheduleWorker.syncFromPrefs failed", it) }
    }

    override fun provideContainer(): AppContainer = container

    private fun applyPersistedTheme() {
        val mode = container.securePrefs.themeMode
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> mode
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private companion object {
        const val TAG = "SecureNetApp"
    }
}
