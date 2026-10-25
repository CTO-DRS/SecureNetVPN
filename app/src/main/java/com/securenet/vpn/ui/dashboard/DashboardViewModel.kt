package com.securenet.vpn.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.ConnectionStats
import com.securenet.vpn.data.model.ScanResult
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.domain.usecase.ConnectVpnUseCase
import com.securenet.vpn.domain.usecase.DisconnectVpnUseCase
import com.securenet.vpn.domain.usecase.ObserveVpnStateUseCase
import com.securenet.vpn.intelligence.NetworkTrustEngine
import com.securenet.vpn.intelligence.SecurityScoreEngine
import com.securenet.vpn.intelligence.SmartInsightsEngine
import com.securenet.vpn.intelligence.SpeedTestEngine
import com.securenet.vpn.network.DnsCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
    private val dnsCacheManager: DnsCacheManager,
    // v1.6.3 CRITICAL: must be a CONSTRUCTOR property. The init block below
    // starts coroutines on viewModelScope (Dispatchers.Main.immediate) — when
    // the ViewModel is constructed on the main thread they execute their body
    // synchronously during construction. A body-declared property assigned
    // further down in the class was still JVM-null at that point, producing
    // the v1.6.2 "getSmartInsightsEngine() on a null object reference" crash
    // the moment the dashboard appeared. Constructor properties are assigned
    // before any init block / property initializer runs — immune by design.
    private val containerRef: AppContainer
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = observeState.state

    val stats: StateFlow<ConnectionStats> = observeState.stats

    private val _selectedServer = MutableStateFlow<VpnServer?>(null)
    val selectedServer: StateFlow<VpnServer?> = _selectedServer.asStateFlow()

    private val _dnsStats = MutableStateFlow("")
    val dnsStats: StateFlow<String> = _dnsStats.asStateFlow()

    /** v1.1: composite on-device security score + recommendation actions. */
    private val _securityScore = MutableStateFlow<SecurityScoreEngine.ScoreReport?>(null)
    val securityScore: StateFlow<SecurityScoreEngine.ScoreReport?> =
        _securityScore.asStateFlow()

    /** v1.1: speed test UI state. */
    data class SpeedUi(
        val running: Boolean = false,
        val pingMs: Int = -1,
        val downMbps: Double = 0.0,
        val upMbps: Double = 0.0,
        val error: Boolean = false
    )

    private val _speedTest = MutableStateFlow(loadLastSpeed())
    val speedTest: StateFlow<SpeedUi> = _speedTest.asStateFlow()

    /** v1.1: Smart Connect suggestion from the on-device recommender. */
    private val _smartSuggestion = MutableStateFlow<VpnServer?>(null)
    val smartSuggestion: StateFlow<VpnServer?> = _smartSuggestion.asStateFlow()

    /** v1.2: DNS Shield live state. */
    data class ShieldUi(val enabled: Boolean = false, val blocked: Long = 0)

    private val _dnsShield = MutableStateFlow(loadShield())
    val dnsShield: StateFlow<ShieldUi> = _dnsShield.asStateFlow()

    /** v1.2: current network trust state. */
    data class TrustUi(
        val transport: String,
        val metered: Boolean,
        val trusted: Boolean,
        val smartRulesEnabled: Boolean
    )

    private val _trust = MutableStateFlow<TrustUi?>(null)
    val trust: StateFlow<TrustUi?> = _trust.asStateFlow()

    /** v1.2: prioritized on-device recommendations. */
    private val _insights =
        MutableStateFlow<List<SmartInsightsEngine.Insight>>(emptyList())
    val insights: StateFlow<List<SmartInsightsEngine.Insight>> =
        _insights.asStateFlow()

    init {
        // v1.6.3: every initial refresh is failure-isolated — a broken prefs
        // file, an engine error or any OEM quirk can degrade that single card
        // to its default state but can never abort ViewModel construction
        // (which would kill the whole dashboard fragment again).
        viewModelScope.launch {
            // v1.6.2: never let the initial server load crash the dashboard.
            _selectedServer.value = runCatching {
                serverRepository.getSelectedServer() ?: serverRepository.getBestServer()
            }.getOrNull()
        }
        runCatching { refreshDnsStats() }
        runCatching { refreshSecurityScore() }
        runCatching { refreshSmartSuggestion() }
        runCatching { refreshInsights() }
        runCatching { refreshTrust() }
    }

    /** Recompute the composite score from the last scan + live state. */
    fun refreshSecurityScore(scanOverride: ScanResult? = null) {
        // v1.6.3: failure-isolated — score card degrades to "unknown" (null).
        viewModelScope.launch {
            val scan = scanOverride ?: withContext(Dispatchers.IO) {
                securePrefs.lastScanJson?.let { raw ->
                    runCatching {
                        val o = org.json.JSONObject(raw)
                        val threatsJson = o.optJSONArray("threats")
                        val threats = (0 until (threatsJson?.length() ?: 0)).mapNotNull { i ->
                            val t = threatsJson!!.getJSONObject(i)
                            com.securenet.vpn.data.model.ThreatReport(
                                id = t.optString("id"),
                                title = t.optString("title"),
                                description = t.optString("description"),
                                recommendation = t.optString("recommendation"),
                                level = com.securenet.vpn.data.model.ThreatLevel.entries
                                    .firstOrNull { it.name == t.optString("level") }
                                    ?: com.securenet.vpn.data.model.ThreatLevel.SAFE,
                                packageName = t.optString("packageName").ifEmpty { null }
                            )
                        }
                        ScanResult(
                            threats = threats,
                            appsScanned = o.optInt("appsScanned", 0),
                            filesScanned = o.optInt("filesScanned", 0),
                            durationMillis = o.optLong("durationMillis", 0),
                            rootDetected = o.optBoolean("rootDetected", false),
                            completedAtMillis = o.optLong("completedAtMillis", 0)
                        )
                    }.getOrNull()
                }
            }
            _securityScore.value = runCatching {
                containerRef.securityScoreEngine.compute(
                    lastScan = scan,
                    connectionState = connectionState.value,
                    dnsCacheEnabled = securePrefs.dnsCacheEnabled,
                    autoMtuEnabled = securePrefs.autoMtu,
                    autoReconnectEnabled = securePrefs.autoReconnect,
                    batterySaverEnabled = securePrefs.batterySaverMode
                )
            }.getOrNull()
        }
    }

    /** Run the Cloudflare-backed speed probe and persist the history. */
    fun runSpeedTest(target: VpnServer?) {
        if (_speedTest.value.running) return
        _speedTest.value = SpeedUi(running = true)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    containerRef.speedTestEngine.run(
                        target?.endpointHost ?: "speed.cloudflare.com",
                        target?.endpointPort ?: 443
                    )
                }
            }.getOrNull()
            if (result != null) {
                securePrefs.lastSpeedTest = result.toJson()
                val history = SpeedTestEngine.History.fromJson(securePrefs.speedTestHistory)
                val updated = SpeedTestEngine.History.appended(history, result)
                securePrefs.speedTestHistory = updated.toJson()
                _speedTest.value = SpeedUi(
                    running = false,
                    pingMs = result.pingMs,
                    downMbps = result.downloadMbps,
                    upMbps = result.uploadMbps
                )
            } else {
                _speedTest.value = SpeedUi(running = false, error = true)
            }
        }
    }

    /** Compute (and persist) the Smart Connect recommendation. */
    fun refreshSmartSuggestion() {
        viewModelScope.launch {
            val servers = runCatching { serverRepository.getServers(false) }
                .getOrDefault(emptyList())
            if (servers.isEmpty()) return@launch
            val current = _selectedServer.value
            val lastUsed = current?.id ?: securePrefs.selectedServerId
            val latencies = runCatching {
                org.json.JSONObject(securePrefs.latencyCache).let { o ->
                    servers.mapNotNull { s ->
                        if (o.has(s.id)) s.id to o.getInt(s.id) else null
                    }.toMap()
                }
            }.getOrDefault(emptyMap())
            val best = runCatching {
                containerRef.serverRecommender.recommend(servers, lastUsed, latencies)
            }.getOrNull()
            _smartSuggestion.value = best?.server?.takeIf { it.id != current?.id }
        }

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

    private fun loadLastSpeed(): SpeedUi {
        val last = securePrefs.lastSpeedTest
            ?.let { SpeedTestEngine.Result.fromJson(it) }
            ?: return SpeedUi()
        return SpeedUi(
            running = false,
            pingMs = last.pingMs,
            downMbps = last.downloadMbps,
            upMbps = last.uploadMbps
        )
    }

    fun refreshDnsStats() {
        _dnsStats.value = runCatching {
            if (securePrefs.dnsCacheEnabled) dnsCacheManager.statsSnapshot() else "off"
        }.getOrDefault("off")
    }

    // ------------------------------------------------------------------
    // v1.2: DNS Shield + trust + insights
    // ------------------------------------------------------------------

    private fun loadShield(): ShieldUi = ShieldUi(
        enabled = securePrefs.dnsShieldEnabled,
        blocked = securePrefs.dnsShieldBlocked
    )

    fun refreshDnsShield() {
        _dnsShield.value = loadShield()
    }

    fun setDnsShield(enabled: Boolean) {
        securePrefs.dnsShieldEnabled = enabled
        _dnsShield.value = ShieldUi(enabled, securePrefs.dnsShieldBlocked)
        refreshInsights()
    }

    fun refreshTrust() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                runCatching { containerRef.networkMonitor.currentSnapshot() }.getOrNull()
            } ?: return@launch
            _trust.value = runCatching {
                val trusted = containerRef.networkTrustEngine.trustListFromJson(
                    securePrefs.trustedNetworks
                ).contains(
                    NetworkTrustEngine.Fingerprint(
                        snapshot.transportDescription, snapshot.isMetered
                    )
                )
                TrustUi(
                    transport = snapshot.transportDescription,
                    metered = snapshot.isMetered,
                    trusted = trusted,
                    smartRulesEnabled = securePrefs.smartRulesEnabled
                )
            }.getOrNull()
        }
    }

    /** Trust / un-trust the current network fingerprint. */
    fun toggleCurrentNetworkTrust() {
        val current = _trust.value ?: return
        val engine = containerRef.networkTrustEngine
        val fp = NetworkTrustEngine.Fingerprint(current.transport, current.metered)
        val list = engine.trustListFromJson(securePrefs.trustedNetworks).toMutableList()
        if (!list.remove(fp)) list.add(fp)
        securePrefs.trustedNetworks = engine.trustListToJson(list)
        refreshTrust()
    }

    fun refreshInsights() {
        viewModelScope.launch {
            val scan = runCatching { securePrefs.lastScanJson }.getOrNull()
            val threats = scan?.let { raw ->
                runCatching {
                    val o = org.json.JSONObject(raw)
                    val arr = o.optJSONArray("threats")
                    (0 until (arr?.length() ?: 0)).count { i ->
                        arr!!.getJSONObject(i).optString("level") != "SAFE"
                    }
                }.getOrDefault(0)
            } ?: 0
            val scanAge = securePrefs.lastScanAt.takeIf { it > 0 }?.let {
                System.currentTimeMillis() - it
            }
            // v1.6.3: the exact line from the field crash report — reading the
            // container handle is now safe (constructor property) and the
            // computation itself is failure-isolated (degrades to no insights).
            _insights.value = runCatching {
                containerRef.smartInsightsEngine.generate(
                    SmartInsightsEngine.Inputs(
                        killSwitchEnabled = securePrefs.killSwitchEnabled,
                        dnsShieldEnabled = securePrefs.dnsShieldEnabled,
                        smartRulesEnabled = securePrefs.smartRulesEnabled,
                        autoConnectOnLaunch = securePrefs.autoConnectOnLaunch,
                        hasScanResults = securePrefs.lastScanAt > 0,
                        scanAgeMillis = scanAge,
                        openThreats = threats,
                        connected = connectionState.value == ConnectionState.CONNECTED,
                        batterySaver = securePrefs.batterySaverMode
                    )
                )
            }.getOrDefault(emptyList())
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
            container.dnsCacheManager,
            container
        ) as T
    }
}
