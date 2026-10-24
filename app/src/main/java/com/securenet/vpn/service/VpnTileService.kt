package com.securenet.vpn.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.securenet.vpn.R
import com.securenet.vpn.data.model.ConnectionState

/**
 * Quick Settings tile: one tap toggles the tunnel without opening the app.
 *
 *  - tap while DISCONNECTED -> reconnect from the cached server config
 *  - tap while CONNECTED/CONNECTING -> disconnect
 *
 * The service itself handles "no cached server" gracefully (fails fast and
 * stops), so the tile never leaves the system in a stuck state.
 */
class VpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val state = SecureNetVpnService.currentState()
        when (state) {
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING,
            ConnectionState.REQUESTING_PERMISSION -> SecureNetVpnService.stop(this)

            else -> SecureNetVpnService.requestReconnect(this)
        }
        // Optimistic flip; onStartListening corrects it when state settles.
        updateTile(if (state == ConnectionState.CONNECTED) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE)
    }

    private fun refreshTile() {
        val state = SecureNetVpnService.currentState()
        val active = state == ConnectionState.CONNECTED ||
            state == ConnectionState.CONNECTING ||
            state == ConnectionState.RECONNECTING
        updateTile(if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE)
    }

    private fun updateTile(state: Int) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification_shield)
        tile.subtitle = when (state) {
            Tile.STATE_ACTIVE -> getString(R.string.dashboard_status_connected)
            Tile.STATE_INACTIVE -> getString(R.string.dashboard_status_disconnected)
            else -> getString(R.string.dashboard_status_connecting)
        }
        tile.updateTile()
    }

    companion object {
        /** Launch helper used by settings deep links and diagnostics. */
        fun requestTileListen(context: Context) {
            if (Build.VERSION.SDK_INT >= 24) {
                ContextCompat.startForegroundService(
                    context,
                    Intent().setComponent(
                        ComponentName(context, VpnTileService::class.java)
                    )
                )
            }
        }
    }
}
