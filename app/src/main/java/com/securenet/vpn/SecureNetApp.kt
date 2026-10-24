package com.securenet.vpn

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.work.WorkScheduler

/**
 * Application entry point. Owns the manual dependency graph (AppContainer),
 * applies app-wide configuration (Material You dynamic colors, persisted
 * theme mode, periodic WorkManager jobs) and exposes the container to
 * Services through the [AppProvider] contract.
 */
class SecureNetApp : Application(), AppProvider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        DynamicColors.applyToActivitiesIfAvailable(this)

        applyPersistedTheme()

        // Periodic connection-log flush + daily usage aggregation.
        WorkScheduler.scheduleAll(this)

        // v1.3: re-arm the connection-schedule tick when a schedule is set.
        com.securenet.vpn.work.ScheduleWorker.syncFromPrefs(this)
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
}
