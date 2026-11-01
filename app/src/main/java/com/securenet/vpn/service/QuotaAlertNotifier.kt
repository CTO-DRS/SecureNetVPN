package com.securenet.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.data.local.HistoryParser
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.stats.DataQuotaEngine
import com.securenet.vpn.stats.DataQuotaEngine.Level
import com.securenet.vpn.stats.MonthlyStatsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * v1.8.24 — the monthly data-quota alert («حصة البيانات الشهرية»).
 * The user names a per-month cap in Settings; this notifier watches the
 * month-to-date traffic ALREADY RECORDED in the encrypted session log
 * (the same [MonthlyStatsEngine] numbers the dashboard draws) and posts
 * one heads-up per threshold crossing — 80% warn, 100% exceeded.
 *
 * Deliberate contrast with the VPN status notification: the status shade
 * is silent/low-importance and refreshes constantly, while this alert is
 * a real event on its own DEFAULT-importance channel — it can be tuned
 * or muted independently in the system settings.
 *
 * Honesty rules (all enforced by the pure [DataQuotaEngine]):
 *  - an alert fires only when the level ladder ADVANCES — repeats and
 *    regressions never re-notify;
 *  - the evaluated level is persisted after every check, so a new month,
 *    a raised cap or cleared logs re-arm the ladder silently;
 *  - a disabled (<= 0) cap cancels any posted alert and resets the
 *    marker; it never evaluates.
 *
 * All work runs off the main thread ([Dispatchers.IO]) — the encrypted
 * log read is the same best-effort read the workers do, empty on failure.
 * Callers are cheap and throttled: [evaluateSoon] may be called every
 * sampler tick; the real check runs at most once per [MIN_CHECK_INTERVAL_MS].
 */
class QuotaAlertNotifier(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastCheckAt: Long = 0L

    init {
        createChannel()
    }

    /**
     * Throttled entry point: [force] bypasses the interval (tunnel-up and
     * app-open want a fresh verdict), otherwise repeated calls inside
     * [MIN_CHECK_INTERVAL_MS] are no-ops. Returns immediately; the check
     * itself runs on IO.
     */
    fun evaluateSoon(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckAt < MIN_CHECK_INTERVAL_MS) return
        lastCheckAt = now
        scope.launch { evaluateNow() }
    }

    private fun evaluateNow() {
        val container = (context.applicationContext as AppProvider).provideContainer()
        val prefs = container.securePrefs
        val quotaBytes = prefs.dataQuotaBytes

        if (quotaBytes <= 0L) {
            if (prefs.dataQuotaLevel != DataQuotaEngine.Level.NONE.stored) {
                prefs.dataQuotaLevel = DataQuotaEngine.Level.NONE.stored
            }
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        val used = monthToDateBytes(container)
        val state = DataQuotaEngine.evaluate(used, quotaBytes)
        val previousStored = prefs.dataQuotaLevel
        prefs.dataQuotaLevel = state.level.stored

        when {
            DataQuotaEngine.shouldAlert(previousStored, state.level) -> {
                container.connectionLogger.appendCustom(
                    "quota_alert", "level=${state.level.name};used=${state.usedBytes}"
                )
                postAlert(state)
            }
            state.level == Level.NONE -> notificationManager.cancel(NOTIFICATION_ID)
            // Same-level repeat (WARN while already WARN): stay silent — the
            // posted notification, if any, already says everything.
        }
    }

    /** Month-to-date total from the encrypted log; 0 on any read failure. */
    private fun monthToDateBytes(container: AppContainer): Long = try {
        val timeline = HistoryParser().parse(container.connectionLogger.readAllLines())
        val nowUtc = LocalDate.now(ZoneOffset.UTC)
        MonthlyStatsEngine.compute(
            usage = timeline.usage,
            sessions = timeline.sessions,
            year = nowUtc.year,
            month = nowUtc.monthValue,
            nowUtc = nowUtc
        ).totalBytes
    } catch (_: Exception) {
        0L
    }

    private fun postAlert(state: DataQuotaEngine.QuotaState) {
        val exceeded = state.level == Level.EXCEEDED
        val title = context.getString(
            if (exceeded) R.string.quota_alert_exceeded_title
            else R.string.quota_alert_warn_title
        )
        val body = context.getString(
            R.string.quota_alert_body,
            MonthlyStatsEngine.formatBytes(state.usedBytes),
            state.percent,
            MonthlyStatsEngine.formatBytes(state.remainingBytes)
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chart)
            .setContentIntent(contentIntent)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(
                context.getColor(
                    if (exceeded) R.color.status_failed else R.color.status_connecting
                )
            )
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.quota_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.quota_channel_desc)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "quota_channel"
        const val NOTIFICATION_ID = 0x5E2

        /** The quota is monthly-scale — one real check per 10 minutes is plenty. */
        const val MIN_CHECK_INTERVAL_MS = 10L * 60_000L
    }
}
