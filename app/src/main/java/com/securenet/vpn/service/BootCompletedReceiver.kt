package com.securenet.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.securenet.vpn.di.AppProvider

/**
 * Restores the tunnel after a reboot when auto-connect is enabled and the
 * session was active before shutdown. Foreground-service starts are permitted
 * from BOOT_COMPLETED as long as the notification is shown immediately.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val container = (context.applicationContext as AppProvider).provideContainer()
        val prefs = container.securePrefs

        // v1.3: re-arm the connection-schedule tick after reboot.
        com.securenet.vpn.work.ScheduleWorker.syncFromPrefs(context.applicationContext)

        // v1.6: Always-on VPN re-establishes the tunnel after a reboot even
        // when the tunnel was down at shutdown; the legacy auto-connect path
        // still requires the pre-shutdown state.
        if (!prefs.alwaysOnEnabled &&
            (!prefs.autoConnectOnBoot || !prefs.wasConnectedBeforeShutdown)
        ) {
            return
        }

        val go = Intent(context, SecureNetVpnService::class.java)
            .setAction(VpnNotificationManager.ACTION_CONNECT)
        goAsync()
        try {
            context.startForegroundService(go)
        } catch (_: Exception) {
            // If the OS refuses (rare OEM policy), the user reconnects manually.
        }
    }
}
