package com.securenet.vpn.domain.repository

import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.ConnectionStats
import com.securenet.vpn.data.model.ScanResult
import com.securenet.vpn.data.model.VpnServer
import kotlinx.coroutines.flow.StateFlow

/** Contract for controlling and observing the WireGuard tunnel. */
interface VpnRepository {

    val state: StateFlow<ConnectionState>

    val stats: StateFlow<ConnectionStats>

    /**
     * Starts the foreground VPN service configured for [server].
     * Returns failure when the system VPN consent dialog is still pending.
     */
    suspend fun connect(server: VpnServer): Result<Unit>

    suspend fun disconnect(): Result<Unit>

    /** Requests a tunnel re-establishment after a network switch. */
    suspend fun reconnect(): Result<Unit>

    fun setBatterySaver(enabled: Boolean)
}

/** Server list sourcing (remote config + encrypted cache). */
interface ServerRepository {
    suspend fun getServers(forceRefresh: Boolean): List<VpnServer>
    suspend fun getBestServer(): VpnServer?
    suspend fun getSelectedServer(): VpnServer?
    fun selectServer(server: VpnServer)
}

/** On-device malware / privacy audit. */
interface ScannerRepository {
    suspend fun fullScan(): ScanResult
}

/** Remote configuration bootstrap (servers + certificate pins + flags). */
interface RemoteConfigRepository {
    suspend fun fetch(forceRefresh: Boolean): Boolean
    fun cachedServers(): List<VpnServer>
    fun cachedCertificatePins(): List<String>
}
