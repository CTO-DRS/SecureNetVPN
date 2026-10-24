package com.securenet.vpn.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.service.SecureNetVpnService

/**
 * Aggregates daily tunnel usage (rx/tx/connected time) into the encrypted
 * log so the dashboard can show honest historical numbers.
 */
class UsageStatsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AppProvider).provideContainer()
        return try {
            val stats = SecureNetVpnService.stats.value
            val dayEpoch = System.currentTimeMillis() / DAY_MS
            container.connectionLogger.appendUsage(
                dayEpoch = dayEpoch,
                rxBytes = stats.rxBytes,
                txBytes = stats.txBytes,
                connectedSeconds = stats.durationMillis / 1000
            )
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "securenet_usage_stats"
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
