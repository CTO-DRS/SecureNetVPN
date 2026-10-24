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

    private val lastRx = MutableStateFlow(0L)
    private val lastTx = MutableStateFlow(0L)

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
            VpnNotificationManager.ACTION_DISCONNECT -> {
                tearDown(reason = "notification_action")
                return START_NOT_STICKY
            }
            VpnNotificationManager.ACTION_CONNECT -> {
                // Reconnect path from the notification: cached config in prefs.
                connectFromCachedConfig()
                return START_STICKY
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

        notificationManager.updateState(ConnectionState.CONNECTED, server.name)
        startStatsSampler()
        startNetworkWatcher()
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
                }
            }
        }
    }

    private fun startNetworkWatcher() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            networkMonitor.networkChanges.collect {
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
            }
        }
    }

    private fun tearDownTunnelOnly() {
        statsJob?.cancel()
        statsJob = null
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
        // Rebuild settings from the cached server; private key stays encrypted.
        val settings = buildCachedSettings(server)
        if (settings == null) {
            publishState(ConnectionState.FAILED, "no_cached_key")
            stopSelf()
            return
        }
        activeSettings = settings
        activeMtu = DEFAULT_MTU
        activeDnsServers = listOf(server.dnsPrimary, server.dnsSecondary)
        establishTunnel()
    }

    private fun buildCachedSettings(server: VpnServer): String? {
        val container = (application as AppProvider).provideContainer()
        val privateKey = container.securePrefs.devicePrivateKey ?: return null
        return KeyUtils.buildWgUserspaceSettings(
            privateKeyBase64 = privateKey,
            peerPublicKeyBase64 = server.publicKey,
            endpointHost = server.endpointHost,
            endpointPort = server.endpointPort,
            allowedIps = if (server.supportsIpv6) {
                listOf(KeyUtils.ROUTE_ALL_V4, KeyUtils.ROUTE_ALL_V6)
            } else {
                listOf(KeyUtils.ROUTE_ALL_V4)
            }
        )
    }

    private fun publishState(state: ConnectionState, detail: String?) {
        stateFlow.value = state
        detail?.let { logger.appendEvent(state, activeServer?.id, it) }
        if (state != ConnectionState.CONNECTED && state != ConnectionState.CONNECTING) {
            notificationManager.updateState(state, activeServer?.name)
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

    /** Small routing constants kept separate for test readability. */
    private object KeyUtilsRoute {
        const val V4_ADDR = "0.0.0.0"
        const val V4_PREFIX = 0
        const val V6_ADDR = "::"
        const val V6_PREFIX = 0
    }

    companion object {
        private const val EXTRA_TUNNEL_CONFIG = "tunnel_config"
        private const val DEFAULT_MTU = 1420
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

        fun notifyBatterySaverChanged(context: Context) {
            // The stats sampler reads the pref on each tick; nothing else to do.
        }

        /** Convenience read for tests and MainActivity. */
        fun currentState(): ConnectionState = stateFlow.value
    }
}
