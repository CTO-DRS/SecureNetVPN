package com.securenet.vpn.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.securenet.vpn.data.local.ConnectionLogger
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.ConnectionStats
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.network.NetworkMonitor
import com.securenet.vpn.nativelib.WgNative
import com.securenet.vpn.tor.TorMode
import com.securenet.vpn.util.KeyUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The WireGuard tunnel. Runs as a specialUse foreground service with the
 * persistent "vpn_channel" notification, owns the TUN device handed over by
 * VpnService.Builder, and drives the wireguard-go backend through WgNative.
 *
 * Responsibilities:
 *  - one-click connect/disconnect from UI or notification
 *  - auto-reconnect on network switches (NetworkMonitor)
 *  - battery-saver aware stats sampling (TrafficStats deltas)
 *  - encrypted session logging
 */
class SecureNetVpnService : VpnService() {

    /**
     * Replaces the coroutine scope a LifecycleService would provide. The
     * service must extend VpnService directly so VpnService.Builder and
     * establish() are available on the implicit receiver.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var notificationManager: VpnNotificationManager
    private lateinit var logger: ConnectionLogger
    private lateinit var networkMonitor: NetworkMonitor

    private var tunFd: ParcelFileDescriptor? = null
    private var wgHandle: Int = INVALID_HANDLE
    private var activeServer: VpnServer? = null
    private var activeSettings: String? = null
    private var activeMtu: Int = DEFAULT_MTU
    private var activeDnsServers: List<String> = emptyList()
    private var activeLocalAddress: String = DEFAULT_LOCAL_V4
    private var activeLocalPrefix: Int = 32
    private var statsJob: Job? = null
    private var monitorJob: Job? = null
    private var recoveryJob: Job? = null

    private val lastRx = MutableStateFlow(0L)
    private val lastTx = MutableStateFlow(0L)

    /**
     * v1.5 — per-session usage accounting. The stats sampler already computes
     * device-wide TrafficStats deltas each tick; those deltas are accumulated
     * here and flushed to the encrypted log every [USAGE_FLUSH_MS] so the
     * monthly dashboard sums true deltas (never cumulative snapshots).
     */
    private var usageAccumRx = 0L
    private var usageAccumTx = 0L
    private var usageLastFlushAt = 0L

    override fun onCreate() {
        super.onCreate()
        val provider = application as AppProvider
        val container = provider.provideContainer()
        notificationManager = VpnNotificationManager(this)
        logger = container.connectionLogger
        networkMonitor = container.networkMonitor
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            VpnNotificationManager.ACTION_DISCONNECT,
            ACTION_DISCONNECT_WIDGET -> {
                tearDown(reason = "stop_action")
                return START_NOT_STICKY
            }
            VpnNotificationManager.ACTION_CONNECT -> {
                // Reconnect path from the notification: cached config in prefs.
                connectFromCachedConfig()
                return START_STICKY
            }
            // v1.3: connection-schedule executor actions.
            ACTION_SCHEDULE_CONNECT -> {
                logger.appendCustom("schedule_fire", "connect")
                connectFromCachedConfig()
                return START_STICKY
            }
            ACTION_SCHEDULE_DISCONNECT -> {
                tearDown(reason = "schedule_window_end")
                return START_NOT_STICKY
            }
            else -> {
                val config = intent?.getStringExtra(EXTRA_TUNNEL_CONFIG)
                if (config != null) {
                    startTunnel(config)
                } else {
                    connectFromCachedConfig()
                }
                return START_STICKY
            }
        }
    }

    // ------------------------------------------------------------------
    // Tunnel lifecycle
    // ------------------------------------------------------------------

    private fun startTunnel(configJson: String) {
        val server = runCatching { parseConfig(configJson) }.getOrNull()
        if (server == null) {
            publishState(ConnectionState.FAILED, "malformed_config")
            tearDown(reason = "malformed_config")
            return
        }
        activeServer = server
        activeSettings = configExtra(configJson, "settings")
        activeMtu = configExtra(configJson, "mtu")?.toIntOrNull() ?: DEFAULT_MTU
        activeDnsServers = configExtra(configJson, "dns")?.split(",")?.filter { it.isNotBlank() }
            ?: emptyList()
        activeLocalAddress = configExtra(configJson, "address") ?: DEFAULT_LOCAL_V4
        activeLocalPrefix = configExtra(configJson, "prefix")?.toIntOrNull() ?: 32

        establishTunnel()
    }

    private fun establishTunnel() {
        val server = activeServer ?: return publishState(ConnectionState.FAILED, "no_server")
        val settings = activeSettings ?: return publishState(ConnectionState.FAILED, "no_settings")

        publishState(ConnectionState.CONNECTING, null)
        notificationManager.postConnecting(this)

        // 1. System VPN consent.
        val consentIntent = VpnService.prepare(this)
        if (consentIntent != null) {
            pendingConsentIntent = consentIntent
            publishState(ConnectionState.REQUESTING_PERMISSION, null)
            return
        }

        // 2. Bring the TUN interface up.
        val builder = Builder()
            .setSession("SecureNet · ${server.name}")
            .setMtu(activeMtu)
            .addAddress(activeLocalAddress, activeLocalPrefix)
        activeDnsServers.distinct().forEach { builder.addDnsServer(it) }
        builder.addRoute(KeyUtilsRoute.V4_ADDR, KeyUtilsRoute.V4_PREFIX)
        if (server.supportsIpv6) {
            builder.addRoute(KeyUtilsRoute.V6_ADDR, KeyUtilsRoute.V6_PREFIX)
        }
        applySplitTunnel(builder)
        val established = runCatching { builder.establish() }
            .onFailure { publishState(ConnectionState.FAILED, "tun_error: ${it.message}") }
            .getOrNull()
        if (established == null) {
            tearDown(reason = "tun_establish_failed")
            return
        }
        tunFd = established

        // 3. Hand the fd to wireguard-go (or simulate when the Go .so is absent).
        serviceScope.launch {
            if (WgNative.isAvailable) {
                val fd = runCatching { tunFd!!.detachFd() }.getOrElse {
                    publishState(ConnectionState.FAILED, "fd_error")
                    return@launch
                }
                wgHandle = WgNative.wgTurnOn("securenet0", fd, settings)
                if (wgHandle < 0) {
                    publishState(ConnectionState.FAILED, "wg_turn_on_failed:$wgHandle")
                    tearDown(reason = "wg_turn_on_failed")
                    return@launch
                }
            } else {
                // Simulation mode keeps the UI/UX demonstrable without the Go
                // backend; a warning is written to the encrypted session log.
                logger.appendEvent(
                    ConnectionState.CONNECTING,
                    server.id,
                    "simulation_mode: libwgcore.so not built"
                )
                wgHandle = SIMULATED_HANDLE
            }

            onTunnelUp(server)
        }
    }

    private fun onTunnelUp(server: VpnServer) {
        publishState(ConnectionState.CONNECTED, null)
        logger.appendEvent(ConnectionState.CONNECTED, server.id)
        (application as AppProvider).provideContainer().securePrefs
            .wasConnectedBeforeShutdown = true

        orchestrateTorMode()
        guardHotspotClients()

        notificationManager.updateState(ConnectionState.CONNECTED, server.name)
        startStatsSampler()
        startNetworkWatcher()
    }

    /**
     * v1.4: VPN Hotspot leak guard. When the phone shares its connection
     * over a hotspot, every client rides this tunnel — if the kill switch
     * is off, a silent tunnel drop would expose all of them. The guard
     * records the risk in the session log so the user can audit it.
     */
    private fun guardHotspotClients() {
        val container = (application as AppProvider).provideContainer()
        val hotspot = container.hotspotShareManager
        val tetherActive = hotspot.refresh()
        val risk = com.securenet.vpn.hotspot.HotspotShare.leakRisk(
            tetherActive = tetherActive,
            vpnConnected = true,
            killSwitchEnabled = container.securePrefs.killSwitchEnabled
        )
        when (risk) {
            com.securenet.vpn.hotspot.HotspotLeakRisk.HIGH ->
                logger.appendCustom("hotspot_leak_risk", "high_enable_kill_switch")
            com.securenet.vpn.hotspot.HotspotLeakRisk.LOW ->
                logger.appendCustom("hotspot_shared", "clients_protected")
            com.securenet.vpn.hotspot.HotspotLeakRisk.NONE -> Unit
        }
    }

    /**
     * v1.3: Tor-over-VPN orchestration. When the mode is armed the tunnel
     * additionally rides Orbot's local SOCKS endpoint; the service verifies
     * Orbot's presence, requests Tor start and records the decision in the
     * session log so the user can audit exactly what happened.
     */
    private fun orchestrateTorMode() {
        val container = (application as AppProvider).provideContainer()
        val enabled = container.securePrefs.torOverVpnEnabled
        val tor = container.torOverVpnManager
        val status = tor.refresh(enabled)
        val installed = tor.isOrbotInstalled()
        when (TorMode.decide(enabled, installed, status)) {
            TorMode.Action.TOR_OFF -> Unit
            TorMode.Action.NEED_ORBOT -> {
                logger.appendCustom("tor_over_vpn", "orbot_missing")
            }
            TorMode.Action.WAITING -> {
                logger.appendCustom("tor_over_vpn", "waiting_for_tor")
                tor.requestStart()
            }
            TorMode.Action.ACTIVE -> {
                logger.appendCustom(
                    "tor_over_vpn",
                    "active:${TorMode.SOCKS_HOST}:${TorMode.SOCKS_PORT}"
                )
            }
        }
    }

    private fun startStatsSampler() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            val container = (application as AppProvider).provideContainer()
            val batterySaver = { container.securePrefs.batterySaverMode }
            val intervalMillis = { if (batterySaver()) 5_000L else 1_000L }

            val startRx = totalRx()
            val startTx = totalTx()
            lastRx.value = startRx
            lastTx.value = startTx
            usageAccumRx = 0L
            usageAccumTx = 0L
            usageLastFlushAt = System.currentTimeMillis()
            publishStats(
                ConnectionStats(
                    rxBytes = startRx,
                    txBytes = startTx,
                    startedAtMillis = System.currentTimeMillis()
                )
            )

            while (isActive && stateFlow.value == ConnectionState.CONNECTED) {
                delay(intervalMillis())
                val rx = totalRx()
                val tx = totalTx()
                val rxSpeed = ((rx - lastRx.value)).coerceAtLeast(0)
                val txSpeed = ((tx - lastTx.value)).coerceAtLeast(0)
                lastRx.value = rx
                lastTx.value = tx

                // v1.5: bank the true deltas for the monthly dashboard.
                usageAccumRx += rxSpeed
                usageAccumTx += txSpeed
                maybeFlushUsage()

                val current = statsFlow.value
                publishStats(
                    current.copy(
                        rxBytes = rx,
                        txBytes = tx,
                        rxSpeedBytesPerSec = rxSpeed / (intervalMillis() / 1000L),
                        txSpeedBytesPerSec = txSpeed / (intervalMillis() / 1000L)
                    )
                )

                // Keep the Go backend honest: surface a dead session quickly.
                if (wgHandle > 0 && WgNative.isAvailable &&
                    WgNative.wgGetState(wgHandle) != WG_STATE_UP
                ) {
                    publishState(ConnectionState.RECONNECTING, "go_backend_down")
                    if (killSwitchArmed()) {
                        scheduleKillSwitchRecovery()
                    }
                }
            }
        }
    }

    /**
     * Kill-switch watchdog: when enabled, a dead tunnel is recovered with an
     * aggressive capped-backoff reconnect loop instead of waiting for the
     * user to notice. Escape hatch: the loop only arms while the user still
     * wants the tunnel up (state stays RECONNECTING).
     */
    private fun killSwitchArmed(): Boolean =
        (application as AppProvider).provideContainer().securePrefs.killSwitchEnabled

    private fun scheduleKillSwitchRecovery() {
        if (recoveryJob?.isActive == true) return
        recoveryJob = serviceScope.launch {
            var backoff = 500L
            while (isActive && stateFlow.value == ConnectionState.RECONNECTING) {
                delay(backoff)
                if (stateFlow.value != ConnectionState.RECONNECTING) break
                logger.appendEvent(ConnectionState.RECONNECTING, activeServer?.id,
                    "kill_switch_recovery:${backoff}ms")
                tearDownTunnelOnly()
                establishTunnel()
                backoff = (backoff * 2).coerceAtMost(5_000L)
                // establishTunnel flips state to CONNECTING on success or
                // FAILED on hard failure; FAILED loops back here via the
                // sampler's next detection tick.
                if (stateFlow.value == ConnectionState.FAILED) {
                    publishState(ConnectionState.RECONNECTING, "kill_switch_retry")
                }
            }
        }
    }

    private fun startNetworkWatcher() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            networkMonitor.networkChanges.collect { snapshot ->
                val current = stateFlow.value
                if (current == ConnectionState.CONNECTED &&
                    (application as AppProvider).provideContainer().securePrefs.autoReconnect
                ) {
                    publishState(ConnectionState.RECONNECTING, "network_change")
                    logger.appendEvent(ConnectionState.RECONNECTING, activeServer?.id, "network_change")
                    tearDownTunnelOnly()
                    delay(RECONNECT_BACKOFF_MS)
                    establishTunnel()
                }
                evaluateSmartRules(snapshot)
            }
        }
    }

    /**
     * v1.2: Smart Network Rules — when the device lands on an untrusted
     * network while the tunnel is down and the user opted in, bring the
     * tunnel back up from the cached server config.
     */
    private suspend fun evaluateSmartRules(snapshot: NetworkMonitor.NetworkSnapshot) {
        val container = (application as AppProvider).provideContainer()
        val prefs = container.securePrefs
        if (!prefs.smartRulesEnabled) return
        if (prefs.cachedSelectedServer == null) return
        val decision = container.networkTrustEngine.decide(
            fingerprint = com.securenet.vpn.intelligence.NetworkTrustEngine.Fingerprint(
                transport = snapshot.transportDescription,
                metered = snapshot.isMetered
            ),
            trusted = container.networkTrustEngine.trustListFromJson(prefs.trustedNetworks),
            smartRulesEnabled = true,
            connected = stateFlow.value == ConnectionState.CONNECTED ||
                stateFlow.value == ConnectionState.CONNECTING,
            hasInternet = snapshot.hasInternet
        )
        if (decision == com.securenet.vpn.intelligence.NetworkTrustEngine.Action.CONNECT) {
            logger.appendEvent(ConnectionState.CONNECTING, activeServer?.id, "smart_rules_auto_connect")
            connectFromCachedConfig()
        }
    }

    private fun tearDownTunnelOnly() {
        // v1.5: land the accumulated usage before the sampler dies so short
        // sessions and reconnect cycles still reach the dashboard.
        flushUsage()
        statsJob?.cancel()
        statsJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        if (wgHandle > 0 && WgNative.isAvailable) {
            runCatching { WgNative.wgTurnOff(wgHandle) }
        }
        wgHandle = INVALID_HANDLE
        runCatching { tunFd?.close() }
        tunFd = null
    }

    private fun tearDown(reason: String) {
        val wasConnected = stateFlow.value == ConnectionState.CONNECTED
        tearDownTunnelOnly()
        monitorJob?.cancel()
        monitorJob = null
        (application as AppProvider).provideContainer().securePrefs
            .wasConnectedBeforeShutdown = false
        if (wasConnected || stateFlow.value != ConnectionState.DISCONNECTED) {
            publishState(ConnectionState.DISCONNECTED, reason)
        }
        logger.appendEvent(ConnectionState.DISCONNECTED, activeServer?.id, reason)
        notificationManager.cancelAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tearDownTunnelOnly()
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun connectFromCachedConfig() {
        val container = (application as AppProvider).provideContainer()
        val raw = container.securePrefs.cachedSelectedServer
        val server = raw?.let { r ->
            runCatching { VpnServer.fromVpnServerJson(r) }.getOrNull()
        }
        if (server == null) {
            publishState(ConnectionState.FAILED, "no_cached_server")
            stopSelf()
            return
        }
        activeServer = server

        // v1.4: multi-hop on the cached path — swap endpoint to the entry
        // relay, peer keys to the exit server, shrink MTU for the extra hop.
        val chain = com.securenet.vpn.multihop.MultiHopChain.fromJson(
            container.securePrefs.multiHopChain
        )
        val hopPlan = if (chain.enabled) {
            com.securenet.vpn.multihop.MultiHopEngine.plan(
                chain.entryServer(), chain.exitServer()
            )
        } else {
            null
        }
        val effectiveServer = hopPlan?.exit ?: server
        if (hopPlan != null) {
            activeServer = effectiveServer
            logger.appendCustom("multihop_active", hopPlan.label)
        }

        // Rebuild settings from the cached server; private key stays encrypted.
        // v1.5: imported profiles connect with their own PrivateKey/PSK.
        val settings = if (hopPlan != null) {
            val privateKey = container.securePrefs.devicePrivateKey ?: run {
                publishState(ConnectionState.FAILED, "no_cached_key")
                stopSelf()
                return
            }
            KeyUtils.buildWgUserspaceSettings(
                privateKeyBase64 = privateKey,
                peerPublicKeyBase64 = hopPlan.exit.publicKey,
                endpointHost = hopPlan.relayHost,
                endpointPort = hopPlan.relayPort,
                allowedIps = if (hopPlan.exit.supportsIpv6) {
                    listOf(KeyUtils.ROUTE_ALL_V4, KeyUtils.ROUTE_ALL_V6)
                } else {
                    listOf(KeyUtils.ROUTE_ALL_V4)
                }
            )
        } else {
            buildCachedSettings(server)
        }
        if (settings == null) {
            publishState(ConnectionState.FAILED, "no_cached_key")
            stopSelf()
            return
        }
        activeSettings = settings
        activeMtu = if (hopPlan != null) {
            com.securenet.vpn.multihop.MultiHopEngine.innerMtu(DEFAULT_MTU)
        } else {
            DEFAULT_MTU
        }
        activeDnsServers = listOf(effectiveServer.dnsPrimary, effectiveServer.dnsSecondary)
        establishTunnel()
    }

    private fun buildCachedSettings(server: VpnServer): String? {
        val container = (application as AppProvider).provideContainer()
        val privateKey = com.securenet.vpn.util.TunnelKeyResolver
            .privateKeyFor(container.securePrefs, server.id) ?: return null
        return KeyUtils.buildWgUserspaceSettings(
            privateKeyBase64 = privateKey,
            peerPublicKeyBase64 = server.publicKey,
            endpointHost = server.endpointHost,
            endpointPort = server.endpointPort,
            allowedIps = if (server.supportsIpv6) {
                listOf(KeyUtils.ROUTE_ALL_V4, KeyUtils.ROUTE_ALL_V6)
            } else {
                listOf(KeyUtils.ROUTE_ALL_V4)
            },
            presharedKeyBase64 = com.securenet.vpn.util.TunnelKeyResolver
                .presharedKeyFor(container.securePrefs, server.id)
        )
    }

    private fun publishState(state: ConnectionState, detail: String?) {
        stateFlow.value = state
        detail?.let { logger.appendEvent(state, activeServer?.id, it) }
        if (state != ConnectionState.CONNECTED && state != ConnectionState.CONNECTING) {
            notificationManager.updateState(state, activeServer?.name)
        }
        // v1.2: keep the home-screen widget in sync with the tunnel state.
        runCatching {
            com.securenet.vpn.widget.QuickConnectWidgetProvider.pushState(applicationContext)
        }
    }

    private fun publishStats(stats: ConnectionStats) {
        statsFlow.value = stats
    }

    private fun parseConfig(configJson: String): VpnServer? =
        VpnServer.fromVpnServerJson(JSONObject(configJson).getString("server"))

    private fun configExtra(configJson: String, key: String): String? = try {
        JSONObject(configJson).optString(key).takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    private fun totalRx(): Long = android.net.TrafficStats.getTotalRxBytes()
    private fun totalTx(): Long = android.net.TrafficStats.getTotalTxBytes()

    /**
     * v1.5: periodic usage flush — only meaningful while a session is live
     * (usageLastFlushAt is armed by startStatsSampler). Appends the TRUE
     * deltas accumulated since the last flush; HistoryParser re-aggregates
     * per day by summing, so deltas are the only correct unit here.
     */
    private fun maybeFlushUsage() {
        val now = System.currentTimeMillis()
        if (now - usageLastFlushAt < USAGE_FLUSH_MS) return
        flushUsage()
    }

    private fun flushUsage() {
        if (usageLastFlushAt == 0L) return
        val now = System.currentTimeMillis()
        val seconds = ((now - usageLastFlushAt) / 1000L).coerceAtLeast(0)
        if (usageAccumRx <= 0L && usageAccumTx <= 0L && seconds <= 0L) {
            usageLastFlushAt = now
            return
        }
        logger.appendUsage(
            dayEpoch = now / DAY_MS,
            rxBytes = usageAccumRx,
            txBytes = usageAccumTx,
            connectedSeconds = seconds
        )
        usageAccumRx = 0L
        usageAccumTx = 0L
        usageLastFlushAt = now
    }

    /** Small routing constants kept separate for test readability. */
    private object KeyUtilsRoute {
        const val V4_ADDR = "0.0.0.0"
        const val V4_PREFIX = 0
        const val V6_ADDR = "::"
        const val V6_PREFIX = 0
    }

    /**
     * Split tunneling: whitelist / blacklist app routing policy.
     * Packages that fail to resolve (uninstalled since selection) are
     * skipped silently so the tunnel still comes up.
     */
    private fun applySplitTunnel(builder: Builder) {
        val container = (application as AppProvider).provideContainer()
        val policy = com.securenet.vpn.ui.splittunnel.SplitTunnelStore.decode(
            container.securePrefs.splitTunnelApps,
            container.securePrefs.splitTunnelMode
        )
        if (policy.mode == com.securenet.vpn.ui.splittunnel.SplitTunnelStore.Mode.OFF) return
        val pm = packageManager
        policy.packages.forEach { pkg ->
            val ok = runCatching {
                pm.getPackageInfo(pkg, 0)
                if (policy.mode == com.securenet.vpn.ui.splittunnel.SplitTunnelStore.Mode.ALLOW_ONLY) {
                    builder.addAllowedApplication(pkg)
                } else {
                    builder.addDisallowedApplication(pkg)
                }
                true
            }.getOrDefault(false)
            if (!ok) {
                logger.appendEvent(ConnectionState.CONNECTING, activeServer?.id,
                    "split_tunnel_skip:$pkg")
            }
        }
    }

    companion object {
        private const val EXTRA_TUNNEL_CONFIG = "tunnel_config"
        private const val DEFAULT_MTU = 1420
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** v1.5: dashboard usage flush cadence. */
        private const val USAGE_FLUSH_MS = 60_000L

        /** v1.2: widget stop action — kept separate from the notification one. */
        const val ACTION_DISCONNECT_WIDGET = "com.securenet.vpn.action.WIDGET_DISCONNECT"

        /** v1.3: fired by the connection-schedule window. */
        const val ACTION_SCHEDULE_CONNECT = "com.securenet.vpn.action.SCHEDULE_CONNECT"
        const val ACTION_SCHEDULE_DISCONNECT = "com.securenet.vpn.action.SCHEDULE_DISCONNECT"
        private const val DEFAULT_LOCAL_V4 = "10.66.66.2"
        private const val INVALID_HANDLE = -1
        private const val SIMULATED_HANDLE = Int.MIN_VALUE
        private const val WG_STATE_UP = 1
        private const val RECONNECT_BACKOFF_MS = 1_500L

        /** Single source of truth for the UI layer. */
        private val stateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
        val state: StateFlow<ConnectionState> = stateFlow.asStateFlow()

        private val statsFlow = MutableStateFlow(ConnectionStats.EMPTY)
        val stats: StateFlow<ConnectionStats> = statsFlow.asStateFlow()

        /** Set when the system consent dialog is required; MainActivity reads it. */
        @Volatile
        var pendingConsentIntent: Intent? = null
            private set

        fun consumeConsentIntent(): Intent? {
            val intent = pendingConsentIntent
            pendingConsentIntent = null
            return intent
        }

        fun start(
            context: Context,
            server: VpnServer,
            wgSettings: String,
            mtu: Int,
            dnsServers: List<String>,
            localAddress: String,
            localPrefix: Int
        ) {
            val payload = JSONObject().apply {
                put("server", server.toVpnServerJson())
                put("settings", wgSettings)
                put("mtu", mtu)
                put("dns", dnsServers.joinToString(","))
                put("address", localAddress)
                put("prefix", localPrefix)
            }
            val intent = Intent(context, SecureNetVpnService::class.java)
                .putExtra(EXTRA_TUNNEL_CONFIG, payload.toString())
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SecureNetVpnService::class.java)
                .setAction(VpnNotificationManager.ACTION_DISCONNECT)
            context.startService(intent)
        }

        fun requestReconnect(context: Context) {
            val intent = Intent(context, SecureNetVpnService::class.java)
                .setAction(VpnNotificationManager.ACTION_CONNECT)
            context.startService(intent)
        }

        /** v1.3: schedule entry points (use cached server config). */
        fun scheduleConnect(context: Context) {
            val intent = Intent(context, SecureNetVpnService::class.java)
                .setAction(ACTION_SCHEDULE_CONNECT)
            context.startService(intent)
        }

        fun scheduleDisconnect(context: Context) {
            val intent = Intent(context, SecureNetVpnService::class.java)
                .setAction(ACTION_SCHEDULE_DISCONNECT)
            context.startService(intent)
        }

        fun notifyBatterySaverChanged(context: Context) {
            // The stats sampler reads the pref on each tick; nothing else to do.
        }

        /** Convenience read for tests and MainActivity. */
        fun currentState(): ConnectionState = stateFlow.value
    }
}
