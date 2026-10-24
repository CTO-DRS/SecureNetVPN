package com.securenet.vpn.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.ConnectionStats
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.domain.usecase.ConnectVpnUseCase
import com.securenet.vpn.domain.usecase.DisconnectVpnUseCase
import com.securenet.vpn.domain.usecase.ObserveVpnStateUseCase
import com.securenet.vpn.network.DnsCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MVVM dashboard state: tunnel state, live stats, selected server.
 * All tunnel truth lives in the service; this ViewModel mirrors it.
 */
class DashboardViewModel(
    private val connectVpn: ConnectVpnUseCase,
    private val disconnectVpn: DisconnectVpnUseCase,
    private val observeState: ObserveVpnStateUseCase,
    private val serverRepository: com.securenet.vpn.domain.repository.ServerRepository,
    private val securePrefs: SecurePrefs,
    private val dnsCacheManager: DnsCacheManager
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = observeState.state

    val stats: StateFlow<ConnectionStats> = observeState.stats

    private val _selectedServer = MutableStateFlow<VpnServer?>(null)
    val selectedServer: StateFlow<VpnServer?> = _selectedServer.asStateFlow()

    private val _dnsStats = MutableStateFlow("")
    val dnsStats: StateFlow<String> = _dnsStats.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedServer.value = serverRepository.getSelectedServer()
                ?: serverRepository.getBestServer()
        }
        refreshDnsStats()
    }

    /** One-click activation entry point (permission handling sits in UI). */
    fun activate(server: VpnServer? = _selectedServer.value, onNeedConsent: () -> Unit = {}) {
        val target = server ?: return
        viewModelScope.launch {
            // The service flips to REQUESTING_PERMISSION when consent is
            // required; the fragment watches state and drives the dialog.
            connectVpn(target)
                .onSuccess { securePrefs.cachedSelectedServer = target.toVpnServerJson() }
                .onFailure { onNeedConsent() }
        }
    }

    fun deactivate() {
        viewModelScope.launch { disconnectVpn() }
    }

    fun selectServer(server: VpnServer) {
        _selectedServer.value = server
        serverRepository.selectServer(server)
    }

    fun batterySaverEnabled(): Boolean = securePrefs.batterySaverMode

    fun refreshDnsStats() {
        if (securePrefs.dnsCacheEnabled) {
            _dnsStats.value = dnsCacheManager.statsSnapshot()
        } else {
            _dnsStats.value = "off"
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(
            container.connectVpnUseCase,
            container.disconnectVpnUseCase,
            container.observeVpnStateUseCase,
            container.serverRepository,
            container.securePrefs,
            container.dnsCacheManager
        ) as T
    }
}
