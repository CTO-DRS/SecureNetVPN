package com.securenet.vpn.util

import com.securenet.vpn.data.local.CustomServerStore
import com.securenet.vpn.data.local.SecurePrefs

/**
 * v1.5 — Picks the WireGuard secrets for a server about to be connected.
 *
 * Stock fleet servers use the app's generated device identity; imported
 * profiles (QR / .conf / paste) carry their own Interface PrivateKey (and an
 * optional PresharedKey) which must win for the tunnel to handshake.
 */
object TunnelKeyResolver {

    fun privateKeyFor(prefs: SecurePrefs, serverId: String?): String? =
        serverId?.let { id ->
            CustomServerStore.find(prefs.customServersJson, id)?.privateKey
        } ?: prefs.devicePrivateKey

    fun presharedKeyFor(prefs: SecurePrefs, serverId: String?): String? =
        serverId?.let { id ->
            CustomServerStore.find(prefs.customServersJson, id)?.presharedKey
        }
}
