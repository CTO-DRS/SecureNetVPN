package com.securenet.vpn.domain.usecase

import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.ConnectionStats
import com.securenet.vpn.domain.repository.VpnRepository
import kotlinx.coroutines.flow.StateFlow

/** Read-side exposure of tunnel state + live telemetry to the UI layer. */
class ObserveVpnStateUseCase(private val vpnRepository: VpnRepository) {

    val state: StateFlow<ConnectionState> = vpnRepository.state

    val stats: StateFlow<ConnectionStats> = vpnRepository.stats
}
