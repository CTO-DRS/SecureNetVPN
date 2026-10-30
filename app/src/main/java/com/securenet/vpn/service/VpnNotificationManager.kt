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
        buildNotification(state, serverName, null)

    /** Full builder: [stats] enriches the CONNECTED presentation when present. */
    fun buildNotification(
        state: ConnectionState,
        serverName: String?,
        stats: ConnectionStats?
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val connected = state == ConnectionState.CONNECTED
        val connecting = state == ConnectionState.CONNECTING ||
            state == ConnectionState.RECONNECTING

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setColor(context.getColor(R.color.brand_primary))

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
                    builder.setContentText(NotifText.sessionLine(stats.rxBytes, stats.txBytes))
                    builder.setStyle(NotificationCompat.BigTextStyle()
                        .bigText(
                            NotifText.expandedBody(
                                rxBytes = stats.rxBytes,
                                txBytes = stats.txBytes,
                                rxSpeed = stats.rxSpeedBytesPerSec,
                                txSpeed = stats.txSpeedBytesPerSec,
                                startedAtMillis = startedAt,
                                utcOffsetMillis = java.util.TimeZone
                                    .getDefault()
                                    .getOffset(startedAt)
                                    .toLong(),
                                downLabel = context.getString(R.string.notif_expanded_down),
                                upLabel = context.getString(R.string.notif_expanded_up),
                                sinceLabel = context.getString(R.string.notif_expanded_since)
                            )
                        ))
                    builder.addAction(buildAction(ACTION_DISCONNECT, R.string.notif_action_disconnect))
                } else {
                    builder.addAction(buildAction(ACTION_DISCONNECT, R.string.notif_action_disconnect))
                }
            }
            connecting -> {
                builder.setContentTitle(context.getString(R.string.notif_title_connecting))
                    .setContentText(context.getString(R.string.notif_connecting_body))
                    .setProgress(0, 0, true)
            }
            else -> {
                builder.setContentTitle(context.getString(R.string.notif_title_disconnected))
                    .setContentText(context.getString(R.string.notif_disconnected_body))
                    .addAction(buildAction(ACTION_CONNECT, R.string.notif_action_connect))
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

    fun postConnecting(service: android.app.Service) {
        val notification = buildNotification(ConnectionState.CONNECTING, null)
        startForegroundSafe(notification, service)
    }

    fun updateState(state: ConnectionState, serverName: String?) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state, serverName))
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
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state, serverName, stats))
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

        /** Live re-notify floor — the shade refreshes at most twice a second. */
        const val MIN_UPDATE_INTERVAL_MS = 500L
    }
}
