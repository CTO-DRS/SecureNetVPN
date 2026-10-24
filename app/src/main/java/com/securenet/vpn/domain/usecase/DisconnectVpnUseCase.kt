package com.securenet.vpn.domain.usecase

import com.securenet.vpn.data.local.VpnEventLogger
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.domain.repository.VpnRepository

/** Tears down the tunnel and stops the foreground service. */
class DisconnectVpnUseCase(
    private val vpnRepository: VpnRepository,
    private val logger: VpnEventLogger
) {
    suspend operator fun invoke(): Result<Unit> {
        val result = vpnRepository.disconnect()
        result.fold(
            onSuccess = { logger.appendEvent(ConnectionState.DISCONNECTED) },
            onFailure = { logger.appendEvent(ConnectionState.FAILED, detail = it.message) }
        )
        return result
    }
}
