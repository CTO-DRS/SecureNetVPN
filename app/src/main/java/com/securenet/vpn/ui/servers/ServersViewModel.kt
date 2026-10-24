package com.securenet.vpn.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.domain.repository.ServerRepository
import com.securenet.vpn.network.DnsCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServersViewModel(
    private val serverRepository: ServerRepository,
    private val securePrefs: SecurePrefs,
    private val dnsCacheManager: DnsCacheManager
) : ViewModel() {

    private val _servers = MutableStateFlow<List<VpnServer>>(emptyList())
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh(forceRefresh: Boolean) {
        viewModelScope.launch {
            _loading.value = true
            val list = runCatching { serverRepository.getServers(forceRefresh) }
                .getOrDefault(emptyList())
            _servers.value = list
            _selectedId.value = serverRepository.getSelectedServer()?.id
            // Prewarm DNS for the top candidates so connect feels instant.
            dnsCacheManager.prewarm(list.take(3).map { it.endpointHost })
            _loading.value = false
        }
    }

    fun select(server: VpnServer) {
        serverRepository.selectServer(server)
        _selectedId.value = server.id
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ServersViewModel(
            container.serverRepository,
            container.securePrefs,
            container.dnsCacheManager
        ) as T
    }
}
