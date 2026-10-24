package com.securenet.vpn.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Central registration of all periodic jobs. Battery-saver friendly:
 * constraints defer heavy work until the device is charging/unmetered.
 */
object WorkScheduler {

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)

        val logRequest = PeriodicWorkRequestBuilder<ConnectionLogWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        wm.enqueueUniquePeriodicWork(
            ConnectionLogWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            logRequest
        )
        // v1.5: UsageStatsWorker removed — the VPN service flushes true deltas
        // to the session log itself; a periodic worker would double-count.
    }
}
