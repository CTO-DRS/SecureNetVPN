package com.securenet.vpn.data.repository

import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.domain.repository.RemoteConfigRepository
import com.securenet.vpn.domain.repository.ServerRepository

/**
 * Serves the server list from the encrypted remote-config cache and refreshes
 * it in the background. Latency measurements (if any) re-rank the list so the
 * recommended server floats to the top.
 */
class ServerRepositoryImpl(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val securePrefs: SecurePrefs
) : ServerRepository {

    override suspend fun getServers(forceRefresh: Boolean): List<VpnServer> {
        remoteConfigRepository.fetch(forceRefresh)
        val servers = remoteConfigRepository.cachedServers()
        return servers.sortedWith(
            compareBy<VpnServer> { if (it.latencyMs < 0) Int.MAX_VALUE else it.latencyMs }
                .thenBy { it.loadPercent }
        )
    }

    override suspend fun getBestServer(): VpnServer? =
        getServers(forceRefresh = false).firstOrNull()

    override suspend fun getSelectedServer(): VpnServer? {
        val servers = remoteConfigRepository.cachedServers()
        val byId = securePrefs.selectedServerId?.let { id -> servers.firstOrNull { it.id == id } }
        if (byId != null) return byId

        // Offline fallback: the encrypted snapshot of the last selection.
        securePrefs.cachedSelectedServer?.let { raw ->
            return runCatching { VpnServer.fromVpnServerJson(raw) }.getOrNull()
        }
        return null
    }

    override fun selectServer(server: VpnServer) {
        securePrefs.selectedServerId = server.id
        securePrefs.cachedSelectedServer = server.toVpnServerJson()
    }
}
