package com.securenet.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.service.SecureNetVpnService

/**
 * v1.2 — Home-screen quick toggle. One tap connects from the cached server
 * config (or opens the app when nothing is configured yet); a second tap
 * tears the tunnel down. State text/icon is refreshed on every tunnel
 * state change via [pushState].
 */
class QuickConnectWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        render(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        pushState(context)
    }

    companion object {

        private fun activeStates(): Set<ConnectionState> = setOf(
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING
        )

        /** Called by the VPN service whenever the tunnel state changes. */
        fun pushState(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, QuickConnectWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) render(context, manager, ids)
            }
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            val active = SecureNetVpnService.currentState() in activeStates()
            val hasCachedServer = runCatching {
                (context.applicationContext as? com.securenet.vpn.di.AppProvider)
                    ?.provideContainer()?.securePrefs?.cachedSelectedServer != null
            }.getOrDefault(false)

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_quick_connect).apply {
                    setTextViewText(
                        R.id.widgetLabel,
                        context.getString(
                            when {
                                active -> R.string.widget_connected
                                hasCachedServer -> R.string.widget_connect
                                else -> R.string.widget_setup
                            }
                        )
                    )
                    setImageViewResource(
                        R.id.widgetIcon,
                        if (active) R.drawable.ic_lock else R.drawable.ic_power
                    )
                    setOnClickPendingIntent(
                        R.id.widgetRoot,
                        tapIntent(context, active, hasCachedServer)
                    )
                }
                manager.updateAppWidget(id, views)
            }
        }

        private fun tapIntent(context: Context, active: Boolean, hasCachedServer: Boolean): PendingIntent {
            val isServiceIntent = active || hasCachedServer
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (isServiceIntent) {
                val action = if (active) {
                    SecureNetVpnService.ACTION_DISCONNECT_WIDGET
                } else {
                    SecureNetVpnService.ACTION_CONNECT
                }
                val intent = Intent(context, SecureNetVpnService::class.java).setAction(action)
                PendingIntent.getService(context, 0, intent, flags)
            } else {
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java), flags
                )
            }
        }
    }
}
