package com.securenet.vpn.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.service.SecureNetVpnService

/**
 * Periodic maintenance of the encrypted session log: compacts oversized
 * logs by rewriting them as a rolling summary, so storage never grows
 * unbounded on long-running devices.
 */
class ConnectionLogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AppProvider).provideContainer()
        val logger = container.connectionLogger

        return try {
            val sizeBytes = logger.logFileSizeBytes()
            if (sizeBytes > MAX_LOG_BYTES) {
                // Keep only the most recent slice, drop the rest.
                val lines = logger.readAllLines()
                val recent = lines.takeLast(KEEP_RECENT_LINES)
                logger.clear()
                recent.forEach { line ->
                    // Re-append through the custom channel, JSONL preserved.
                    container.connectionLogger.appendCustom("archived", line.take(200))
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "securenet_connection_log"
        const val MAX_LOG_BYTES = 512L * 1024
        const val KEEP_RECENT_LINES = 200
    }
}
