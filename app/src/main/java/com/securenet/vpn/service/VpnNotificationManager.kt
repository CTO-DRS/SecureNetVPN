package com.securenet.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.ConnectionStats
import com.securenet.vpn.di.AppProvider

/**
 * Persistent status notification on the dedicated "vpn_channel" — the
 * app's face in the system notification shade (لوحة الإشعارات). Required
 * by Android for any VPN foreground service; also the primary control
 * surface (tap to open, action button to disconnect/connect).
 *
 * v1.8.21 — a much richer presentation, all of it silent and throttled:
 *  - a live session chronometer next to the title while connected
 *    (Android counts it in the shade — no per-second re-notify needed);
 *  - a compact data line (↓ total · ↑ total) under the title and a
 *    three-line expanded view (download/upload totals with live rates
 *    plus the session-start clock), rebuilt by the pure [NotifText];
 *  - an indeterminate progress pulse while connecting;
 *  - a Connect action when down (the service already handles the
 *    ACTION_CONNECT reconnect path);
 *  - the brand colour on the accent icon and lockscreen visibility
 *    upgraded from SECRET to PRIVATE (presence visible, content hidden).
 *
 * v1.8.23 — «الإشعار في يدك»: the user shapes the presentation:
 *  - [NotifStyle.Style] gates the connected view: DETAILED (title, data
 *    line, expanded body with the new session-average line), COMPACT
 *    (title + data line) or MINIMAL (title only);
 *  - [NotifStyle.Lockscreen] chooses what survives the lock screen —
 *    hide data (default), show everything, or hide entirely;
 *  - the accent tints with the state's own status colour (green/amber/
 *    neutral/red) instead of one static brand hue;
 *  - down states (disconnected/disconnecting/failed) carry the last
 *    session's summary — duration + totals — when one exists;
 *  - connecting names the server it is raising the tunnel to;
 *  - [ACTION_REFRESH] lets Settings re-post the notification instantly
 *    after a style change (via [updateLiveNow], bypassing the throttle).
 *
 * Live stat refreshes flow through [updateLive], which never re-notifies
 * faster than [MIN_UPDATE_INTERVAL_MS] and only while CONNECTED, so the
 * stats sampling loop can call it every tick without waking the user or
 * burning the battery on invisible redraws.
 */
class VpnNotificationManager(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Volatile
    private var lastLivePushAt: Long = 0L

    init {
        createChannel()
    }

    /** What the down state reports about the previous tunnel session. */
    data class LastSession(
        val durationMillis: Long,
        val rxBytes: Long,
        val txBytes: Long
    )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
    }

    /** Legacy entry point — builds the notification without live stats. */
    fun buildNotification(state: ConnectionState, serverName: String?): Notification =
        buildNotification(state, serverName, null, null)

    /** Full builder: [stats] enriches CONNECTED, [lastSession] enriches down states. */
    fun buildNotification(
        state: ConnectionState,
        serverName: String?,
        stats: ConnectionStats?,
        lastSession: LastSession?
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val connected = state == ConnectionState.CONNECTED
        val connecting = state == ConnectionState.CONNECTING ||
            state == ConnectionState.REQUESTING_PERMISSION ||
            state == ConnectionState.RECONNECTING

        // v1.8.23 — the user's presentation policy, read fresh each build
        // so a Settings change applies on the very next re-notify.
        val prefs = (context.applicationContext as AppProvider)
            .provideContainer().securePrefs
        val style = NotifStyle.Style.from(prefs.notifStyle)
        val lockscreen = NotifStyle.Lockscreen.from(prefs.notifLockscreen)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(lockscreen.visibility)
            .setColor(context.getColor(NotifStyle.accentColorRes(state)))

        when {
            connected -> {
                builder.setContentTitle(
                    context.getString(R.string.notif_title_connected, serverName
                        ?: context.getString(R.string.app_name))
                )
                // Live session clock, counted by the system itself.
                val startedAt = stats?.startedAtMillis ?: 0L
                if (startedAt > 0L) {
                    builder.setWhen(startedAt)
                        .setUsesChronometer(true)
                        .setShowWhen(true)
                }
                if (stats != null) {
                    // v1.8.23 — the compact line honours the chosen style
                    // (dropped entirely for MINIMAL).
                    NotifStyle.compactLine(style, stats.rxBytes, stats.txBytes)
                        ?.let { builder.setContentText(it) }
                    if (style == NotifStyle.Style.DETAILED) {
                        builder.setStyle(NotificationCompat.BigTextStyle()
                            .bigText(
                                NotifText.detailedBody(
                                    rxBytes = stats.rxBytes,
                                    txBytes = stats.txBytes,
                                    rxSpeed = stats.rxSpeedBytesPerSec,
                                    txSpeed = stats.txSpeedBytesPerSec,
                                    startedAtMillis = startedAt,
                                    nowMillis = System.currentTimeMillis(),
                                    utcOffsetMillis = java.util.TimeZone
                                        .getDefault()
                                        .getOffset(startedAt)
                                        .toLong(),
                                    downLabel = context.getString(R.string.notif_expanded_down),
                                    upLabel = context.getString(R.string.notif_expanded_up),
                                    sinceLabel = context.getString(R.string.notif_expanded_since),
                                    avgLabel = context.getString(R.string.notif_avg_label)
                                )
                            ))
                    }
                }
                builder.addAction(buildAction(ACTION_DISCONNECT, R.string.notif_action_disconnect))
            }
            connecting -> {
                builder.setContentTitle(context.getString(R.string.notif_title_connecting))
                    .setContentText(
                        if (serverName != null) {
                            context.getString(R.string.notif_connecting_server, serverName)
                        } else {
                            context.getString(R.string.notif_connecting_body)
                        }
                    )
                    .setProgress(0, 0, true)
            }
            else -> {
                // v1.8.23 — a down state reports what the last session
                // achieved instead of a bare generic line.
                builder.setContentTitle(context.getString(R.string.notif_title_disconnected))
                if (lastSession != null) {
                    val summary = NotifText.lastSessionLine(
                        lastSession.durationMillis,
                        lastSession.rxBytes,
                        lastSession.txBytes,
                        context.getString(R.string.notif_last_session_label)
                    )
                    builder.setContentText(summary)
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText(summary))
                } else {
                    builder.setContentText(context.getString(R.string.notif_disconnected_body))
                }
                builder.addAction(buildAction(ACTION_CONNECT, R.string.notif_action_connect))
            }
        }
        return builder.build()
    }

    private fun buildAction(action: String, labelRes: Int): NotificationCompat.Action {
        val intent = PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, SecureNetVpnService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action(
            R.drawable.ic_notification_shield,
            context.getString(labelRes),
            intent
        )
    }

    fun postConnecting(service: android.app.Service, serverName: String?) {
        val notification = buildNotification(ConnectionState.CONNECTING, serverName)
        startForegroundSafe(notification, service)
    }

    fun updateState(state: ConnectionState, serverName: String?, lastSession: LastSession? = null) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state, serverName, null, lastSession))
        if (state != ConnectionState.CONNECTED) lastLivePushAt = 0L
    }

    /**
     * v1.8.21 — live refresh from the stats sampling loop: CONNECTED only,
     * throttled to one re-notify per [MIN_UPDATE_INTERVAL_MS] so the shade
     * never flickers and the sampler can call it every tick.
     */
    fun updateLive(state: ConnectionState, serverName: String?, stats: ConnectionStats?) {
        if (state != ConnectionState.CONNECTED) return
        val now = System.currentTimeMillis()
        if (now - lastLivePushAt < MIN_UPDATE_INTERVAL_MS) return
        lastLivePushAt = now
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state, serverName, stats, null))
    }

    /**
     * v1.8.23 — unthrottled live re-notify for the Settings sheet's
     * ACTION_REFRESH: a style change must repaint immediately, not on the
     * sampler's next tick.
     */
    fun updateLiveNow(state: ConnectionState, serverName: String?, stats: ConnectionStats?) {
        if (state != ConnectionState.CONNECTED) return
        lastLivePushAt = System.currentTimeMillis()
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state, serverName, stats, null))
    }

    fun cancelAll() {
        notificationManager.cancel(NOTIFICATION_ID)
        lastLivePushAt = 0L
    }

    /**
     * foregroundServiceType enforcement differs by API level:
     * 34+ requires passing the declared specialUse type explicitly.
     */
    fun startForegroundSafe(notification: Notification, service: android.app.Service) {
        if (Build.VERSION.SDK_INT >= 34) {
            service.startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "vpn_channel"
        const val NOTIFICATION_ID = 0x5E1
        const val ACTION_DISCONNECT = "com.securenet.vpn.action.DISCONNECT"
        const val ACTION_CONNECT = "com.securenet.vpn.action.CONNECT"

        /** v1.8.23 — Settings sheet: repaint the notification right now. */
        const val ACTION_REFRESH = "com.securenet.vpn.action.REFRESH_NOTIFICATION"

        /** Live re-notify floor — the shade refreshes at most twice a second. */
        const val MIN_UPDATE_INTERVAL_MS = 500L
    }
}
