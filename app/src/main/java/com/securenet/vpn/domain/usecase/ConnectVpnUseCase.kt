package com.securenet.vpn.domain.usecase

import com.securenet.vpn.data.local.VpnEventLogger
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.domain.repository.VpnRepository

/**
 * Activates protection: persists the selection, drives the service start
 * and records the transition in the encrypted session log.
 */
class ConnectVpnUseCase(
    private val vpnRepository: VpnRepository,
    private val logger: VpnEventLogger
) {
    suspend operator fun invoke(server: VpnServer): Result<Unit> {
        if (server.publicKey.isBlank()) {
            logger.appendEvent(ConnectionState.FAILED, server.id, "missing_peer_public_key")
            return Result.failure(IllegalArgumentException("Server has no peer public key"))
        }
        val result = vpnRepository.connect(server)
        result.fold(
            onSuccess = { logger.appendEvent(ConnectionState.CONNECTING, server.id) },
            onFailure = { logger.appendEvent(ConnectionState.FAILED, server.id, it.message) }
        )
        return result
    }
}
