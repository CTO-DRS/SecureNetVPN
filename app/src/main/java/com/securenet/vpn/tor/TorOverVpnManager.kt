package com.securenet.vpn.tor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.3: Tor-over-VPN mode.
 *
 * Routing model (onion over VPN): the WireGuard tunnel handles the transport
 * hop; when Tor mode is armed the session additionally funnels traffic through
 * Orbot's local SOCKS5 endpoint before it exits to the onion network. The app
 * never ships its own Tor daemon — it orchestrates the user's Orbot install
 * through the documented Orbot add-on intents, so no extra permissions and no
 * bundled binaries are required.
 */
enum class TorStatus {
    /** Mode disabled in settings. */
    DISABLED,
    /** Orbot is not installed on this device. */
    NOT_INSTALLED,
    /** Orbot installed but the Tor network is off. */
    OFF,
    /** Tor bootstrap in progress. */
    STARTING,
    /** Tor circuits are up; SOCKS endpoint is live. */
    CONNECTED,
    /** Orbot reported an error or an unknown status payload. */
    FAILED
}

/**
 * Pure decision + parsing logic for the Tor mode. Kept free of any Android
 * import so the unit suite can exercise every branch without Robolectric.
 */
object TorMode {

    /** Orbot's official add-on contract (https://github.com/guardianproject/orbot). */
    const val ORBOT_PACKAGE = "org.torproject.android"
    const val ACTION_START_TOR = "org.torproject.android.intent.action.START"
    const val ACTION_STATUS = "org.torproject.android.intent.action.STATUS"
    const val EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS"
    const val EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME"
    const val EXTRA_SOCKS = "org.torproject.android.intent.extra.SOCKS_PROXY"
    const val CATEGORY_ALL = "org.torproject.android.intent.category.ALL"

    /** Orbot's default local SOCKS5 listener. */
    const val SOCKS_HOST = "127.0.0.1"
    const val SOCKS_PORT = 9050

    enum class Action { TOR_OFF, NEED_ORBOT, WAITING, ACTIVE }

    /**
     * Maps Orbot's status extra to a [TorStatus]. Orbot broadcasts "ON",
     * "STARTING" and "OFF"; anything else (null, garbage) is treated as
     * FAILED so the UI never shows a false "connected" state.
     */
    fun parseStatus(raw: String?): TorStatus = when (raw?.trim()?.uppercase()) {
        "ON" -> TorStatus.CONNECTED
        "STARTING" -> TorStatus.STARTING
        "OFF", "STOPPING" -> TorStatus.OFF
        else -> TorStatus.FAILED
    }

    /**
     * Extracts the SOCKS endpoint Orbot announced in its status broadcast,
     * e.g. "socks://127.0.0.1:9050". Returns null when the payload does not
     * carry a usable localhost endpoint (never trust a non-loopback host —
     * a remote "proxy" would leak traffic outside the device).
     */
    fun parseSocksEndpoint(raw: String?): Pair<String, Int>? {
        val body = raw?.substringAfter("://") ?: return null
        val host = body.substringBefore(":")
        val port = body.substringAfter(":", "").toIntOrNull() ?: return null
        if (host != SOCKS_HOST) return null
        if (port !in 1..65535) return null
        return host to port
    }

    /**
     * What should the VPN service do about Tor right now?
     *
     * @param enabled     Tor mode armed in settings.
     * @param installed   Orbot package visible on the device.
     * @param status      Last known Tor status.
     */
    fun decide(enabled: Boolean, installed: Boolean, status: TorStatus): Action = when {
        !enabled -> Action.TOR_OFF
        !installed -> Action.NEED_ORBOT
        status == TorStatus.CONNECTED -> Action.ACTIVE
        else -> Action.WAITING
    }

    /**
     * Sanity check for the endpoint actually accepting connections. Pure
     * function of the probe result so tests can cover both directions.
     */
    fun resolveStatus(status: TorStatus, socksPortOpen: Boolean): TorStatus =
        if (status == TorStatus.CONNECTED && socksPortOpen) TorStatus.CONNECTED
        else if (status == TorStatus.CONNECTED && !socksPortOpen) TorStatus.STARTING
        else status

    fun isLoopback(host: String): Boolean =
        host == "127.0.0.1" || host == "localhost" || host == "::1"
}

/**
 * Runtime orchestrator: detects Orbot, fires the START_TOR request, listens
 * for status broadcasts and exposes a [StateFlow] the settings UI observes.
 */
class TorOverVpnManager(private val context: Context) {

    private val _status = MutableStateFlow(TorStatus.DISABLED)
    val status: StateFlow<TorStatus> = _status.asStateFlow()

    private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != TorMode.ACTION_STATUS) return
            val raw = intent.getStringExtra(TorMode.EXTRA_STATUS)
            _status.value = TorMode.parseStatus(raw)
        }
    }

    /** Package visibility is declared through <queries> in the manifest. */
    fun isOrbotInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TorMode.ORBOT_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * Re-evaluate the current status from settings state. Called from the
     * service on tunnel-up and from the settings screen on toggle.
     */
    fun refresh(enabled: Boolean): TorStatus {
        if (!enabled) {
            _status.value = TorStatus.DISABLED
            return _status.value
        }
        if (!isOrbotInstalled()) {
            _status.value = TorStatus.NOT_INSTALLED
            return _status.value
        }
        if (_status.value == TorStatus.DISABLED || _status.value == TorStatus.NOT_INSTALLED) {
            _status.value = TorStatus.OFF
        }
        register()
        return _status.value
    }

    /** Ask Orbot to bring up the Tor network (idempotent; Orbot dedupes). */
    fun requestStart() {
        if (!isOrbotInstalled()) return
        register()
        val intent = Intent(TorMode.ACTION_START_TOR).apply {
            setPackage(TorMode.ORBOT_PACKAGE)
            addCategory(TorMode.CATEGORY_ALL)
            putExtra(TorMode.EXTRA_PACKAGE_NAME, context.packageName)
        }
        runCatching { context.startService(intent) }
        if (_status.value == TorStatus.OFF || _status.value == TorStatus.DISABLED) {
            _status.value = TorStatus.STARTING
        }
    }

    private fun register() {
        if (receiverRegistered) return
        val filter = IntentFilter(TorMode.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(statusReceiver, filter)
        }
        receiverRegistered = true
    }

    fun unregister() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(statusReceiver) }
        receiverRegistered = false
    }
}
