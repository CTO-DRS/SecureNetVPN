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

/**
 * Persistent status notification on the dedicated "vpn_channel".
 * Required by Android for any VPN foreground service; also the primary
 * control surface (tap to open, action button to disconnect).
 */
class VpnNotificationManager(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun buildNotification(state: ConnectionState, serverName: String?): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val titleRes = when (state) {
            ConnectionState.CONNECTED -> R.string.notif_title_connected
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING -> R.string.notif_title_connecting
            else -> R.string.notif_title_disconnected
        }

        val text = when (state) {
            ConnectionState.CONNECTED -> serverName ?: context.getString(R.string.app_name)
            else -> context.getString(R.string.app_name)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle(context.getString(titleRes, text).takeIf { state == ConnectionState.CONNECTED }
                ?: context.getString(titleRes))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)

        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            builder.addAction(buildAction(ACTION_DISCONNECT, R.string.notif_action_disconnect))
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
    }

    fun cancelAll() {
        notificationManager.cancel(NOTIFICATION_ID)
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
    }
}
