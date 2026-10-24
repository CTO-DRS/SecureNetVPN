package com.securenet.vpn.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.securenet.vpn.data.local.ConnectionLogger
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.schedule.ConnectionSchedule
import com.securenet.vpn.schedule.ConnectionScheduleEngine
import com.securenet.vpn.schedule.ScheduleAction
import com.securenet.vpn.service.SecureNetVpnService
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * v1.3: Connection scheduling executor. Runs every 15 minutes (the minimum
 * WorkManager period), evaluates the user's schedule window and starts or
 * stops the tunnel accordingly. Idempotent: when the desired state already
 * matches reality it logs nothing and exits — so re-enqueueing is always safe.
 */
class ScheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AppProvider).provideContainer()
        val prefs = container.securePrefs
        val logger = container.connectionLogger

        val schedule = ConnectionSchedule.fromJson(prefs.connectionSchedule)
        if (!schedule.enabled) return Result.success()

        val now = LocalTime.now()
        val dayIso = java.time.LocalDate.now().dayOfWeek.value // 1 = Monday … 7 = Sunday
        val minutes = now.hour * 60 + now.minute
        val connected = SecureNetVpnService.currentState() == ConnectionState.CONNECTED ||
            SecureNetVpnService.currentState() == ConnectionState.CONNECTING

        when (ConnectionScheduleEngine.evaluate(schedule, dayIso, minutes, connected)) {
            ScheduleAction.CONNECT -> {
                logger.appendCustom("schedule_connect:${schedule.connectAt}")
                SecureNetVpnService.requestReconnect(applicationContext)
            }
            ScheduleAction.DISCONNECT -> {
                logger.appendCustom("schedule_disconnect:${schedule.disconnectAt}")
                SecureNetVpnService.stop(applicationContext)
            }
            ScheduleAction.NONE -> Unit
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "connection_schedule_tick"

        /** (Re)arm the scheduler; KEEP policy keeps the original period. */
        fun arm(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun disarm(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /**
         * Called from app start and BOOT_COMPLETED: arm only when the user
         * actually has a schedule enabled, otherwise cancel leftovers.
         */
        fun syncFromPrefs(context: Context) {
            val prefs = (context.applicationContext as AppProvider).provideContainer().securePrefs
            val enabled = ConnectionSchedule.fromJson(prefs.connectionSchedule).enabled
            if (enabled) arm(context) else disarm(context)
        }
    }
}
