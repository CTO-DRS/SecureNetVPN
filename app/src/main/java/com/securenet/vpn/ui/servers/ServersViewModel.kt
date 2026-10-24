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
    private val dnsCacheManager: DnsCacheManager,
    private val recommender: com.securenet.vpn.intelligence.ServerRecommender,
    private val latencyProber: com.securenet.vpn.intelligence.ServerLatencyProber
) : ViewModel() {

    private val _servers = MutableStateFlow<List<VpnServer>>(emptyList())
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** v1.1: Smart Connect live latency sweep + recommendation. */
    private val _smartProbing = MutableStateFlow(false)
    val smartProbing: StateFlow<Boolean> = _smartProbing.asStateFlow()

    private val _smartPicked = MutableStateFlow<VpnServer?>(null)
    val smartPicked: StateFlow<VpnServer?> = _smartPicked.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    /**
     * Smart Connect: TCP-probe every endpoint in parallel (<=1s), feed the
     * measured latencies into the on-device recommender, persist the latency
     * cache, and auto-select the winner.
     */
    fun smartConnect() {
        if (_smartProbing.value) return
        viewModelScope.launch {
            _smartProbing.value = true
            val servers = _servers.value
            if (servers.isEmpty()) {
                _smartProbing.value = false
                return@launch
            }
            val latencies = runCatching {
                latencyProber.probeAll(
                    endpoints = servers.map { it.endpointHost to it.endpointPort },
                    keys = servers.map { it.id }
                )
            }.getOrDefault(emptyMap())

            // Persist the latency cache for the dashboard recommender.
            runCatching {
                val o = org.json.JSONObject(securePrefs.latencyCache)
                latencies.forEach { (id, ms) -> o.put(id, ms) }
                securePrefs.latencyCache = o.toString()
            }

            val lastUsed = securePrefs.selectedServerId
            val best = recommender.recommend(servers, lastUsed, latencies)
            best?.server?.let {
                select(it)
                _smartPicked.value = it
            }
            _smartProbing.value = false
        }
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
            container.dnsCacheManager,
            container.serverRecommender,
            container.latencyProber
        ) as T
    }
}
