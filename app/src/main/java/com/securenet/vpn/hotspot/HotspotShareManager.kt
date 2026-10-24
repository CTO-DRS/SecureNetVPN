package com.securenet.vpn.hotspot

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure hotspot-sharing decision logic — unit-testable without Android.
 *
 * Sharing the VPN over a hotspot protects every connected client device
 * (consoles, TVs, laptops) because their traffic rides the phone's tunnel.
 * The one real danger: clients share the *physical* tether while the tunnel
 * is down or unguarded — that is the leak this model flags.
 */
enum class HotspotLeakRisk { NONE, LOW, HIGH }

object HotspotShare {

    /** System tethering screen action; falls back to wireless settings. */
    const val TETHER_SETTINGS_ACTION = "com.android.settings.TETHER_SETTINGS"
    const val WIRELESS_SETTINGS_ACTION = "android.settings.WIRELESS_SETTINGS"

    /**
     * Risk matrix for clients riding the tether:
     *  - no tethering → NONE (nothing to leak)
     *  - tether + VPN + kill switch → LOW (clients protected; brief
     *    reconnect windows are covered by the kill switch watchdog)
     *  - tether + VPN without kill switch → HIGH (a tunnel drop silently
     *    exposes every client to the physical network)
     *  - tether without VPN → HIGH (all traffic is naked anyway)
     */
    fun leakRisk(
        tetherActive: Boolean,
        vpnConnected: Boolean,
        killSwitchEnabled: Boolean
    ): HotspotLeakRisk = when {
        !tetherActive -> HotspotLeakRisk.NONE
        vpnConnected && killSwitchEnabled -> HotspotLeakRisk.LOW
        else -> HotspotLeakRisk.HIGH
    }

    /** Tether state read as an iface list; any non-blank iface = active. */
    fun parseTetheredIfaces(ifaces: List<String>): Boolean =
        ifaces.any { it.isNotBlank() }

    /** One-line status for the settings row. */
    fun describe(tetherActive: Boolean, vpnConnected: Boolean): String = when {
        !tetherActive -> "off"
        vpnConnected -> "active+vpn"
        else -> "active"
    }
}

/**
 * Runtime helper: reads the OS hotspot state (public WifiManager AP state
 * API), exposes it as a flow and opens the system tethering screen. Real AP
 * start/stop stays with the OS (TETHER_PRIVILEGED is system-only); the app
 * orchestrates + audits instead of fighting the permission model.
 */
class HotspotShareManager(private val context: Context) {

    private val _tetherActive = MutableStateFlow(false)
    val tetherActive: StateFlow<Boolean> = _tetherActive.asStateFlow()

    private val wifi: WifiManager? = runCatching {
        context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }.getOrNull()

    private val connectivity: ConnectivityManager? = runCatching {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()

    /** Legacy WifiManager.WIFI_AP_STATE_ENABLED int (removed from public SDK stubs). */
    private val WIFI_AP_STATE_ENABLED_LEGACY = 13

    /**
     * Support check: the public SDK removed isTetheringSupported(), so it is
     * probed reflectively and treated as unsupported on refusal. A missing
     * answer never crashes the settings screen.
     */
    fun isTetheringSupported(): Boolean = runCatching {
        val cm = connectivity ?: return@runCatching false
        cm.javaClass.getMethod("isTetheringSupported").invoke(cm) as Boolean
    }.getOrDefault(false)

    /**
     * Hotspot state: the AP-state APIs left the public SDK (SystemApi), so
     * the check goes through reflection with graceful degradation — when the
     * OS blocks the probe the app simply reports the hotspot as off and the
     * leak guard stays silent. No crash, no false "active" state.
     */
    fun isHotspotActive(): Boolean {
        val wm = wifi ?: return false

        // Preferred: hidden boolean probe.
        runCatching {
            return wm.javaClass.getMethod("isWifiApEnabled").invoke(wm) as Boolean
        }

        // Fallback: legacy int state (13 = WIFI_AP_STATE_ENABLED).
        runCatching {
            val state = wm.javaClass.getMethod("getWifiApState").invoke(wm) as Int
            return state == WIFI_AP_STATE_ENABLED_LEGACY
        }

        return false
    }

    fun refresh(): Boolean {
        val active = isHotspotActive()
        _tetherActive.value = active
        return active
    }

    /** Opens the system tethering screen (with a wireless-settings fallback). */
    fun openTetheringSettings() {
        val intent = Intent(HotspotShare.TETHER_SETTINGS_ACTION).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.recoverCatching {
            context.startActivity(
                Intent(HotspotShare.WIRELESS_SETTINGS_ACTION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
