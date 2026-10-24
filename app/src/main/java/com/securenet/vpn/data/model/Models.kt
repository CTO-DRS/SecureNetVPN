package com.securenet.vpn.data.model

import org.json.JSONObject

/** A VPN endpoint that can host a WireGuard tunnel. */
data class VpnServer(
    val id: String,
    val name: String,
    val countryCode: String,
    val city: String,
    val endpointHost: String,
    val endpointPort: Int = 51820,
    val publicKey: String,
    val localAddressV4: String = "10.66.66.2",
    val localPrefixV4: Int = 32,
    val dnsPrimary: String = "10.66.66.1",
    val dnsSecondary: String = "1.1.1.1",
    val latencyMs: Int = -1,
    val loadPercent: Int = 0,
    val supportsIpv6: Boolean = false
) {
    fun toVpnServerJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("countryCode", countryCode)
        put("city", city)
        put("endpointHost", endpointHost)
        put("endpointPort", endpointPort)
        put("publicKey", publicKey)
        put("localAddressV4", localAddressV4)
        put("localPrefixV4", localPrefixV4)
        put("dnsPrimary", dnsPrimary)
        put("dnsSecondary", dnsSecondary)
        put("latencyMs", latencyMs)
        put("loadPercent", loadPercent)
        put("supportsIpv6", supportsIpv6)
    }.toString()

    companion object {
        fun fromVpnServerJson(raw: String): VpnServer {
            val o = JSONObject(raw)
            return VpnServer(
                id = o.getString("id"),
                name = o.getString("name"),
                countryCode = o.getString("countryCode"),
                city = o.getString("city"),
                endpointHost = o.getString("endpointHost"),
                endpointPort = o.optInt("endpointPort", 51820),
                publicKey = o.getString("publicKey"),
                localAddressV4 = o.optString("localAddressV4", "10.66.66.2"),
                localPrefixV4 = o.optInt("localPrefixV4", 32),
                dnsPrimary = o.optString("dnsPrimary", "10.66.66.1"),
                dnsSecondary = o.optString("dnsSecondary", "1.1.1.1"),
                latencyMs = o.optInt("latencyMs", -1),
                loadPercent = o.optInt("loadPercent", 0),
                supportsIpv6 = o.optBoolean("supportsIpv6", false)
            )
        }
    }
}

/** Explicit lifecycle of the tunnel, surfaced to the UI as a StateFlow. */
enum class ConnectionState {
    DISCONNECTED,
    REQUESTING_PERMISSION,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    RECONNECTING,
    FAILED
}

/** Snapshot of live tunnel telemetry sampled from TrafficStats. */
data class ConnectionStats(
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxSpeedBytesPerSec: Long = 0L,
    val txSpeedBytesPerSec: Long = 0L,
    val startedAtMillis: Long = 0L
) {
    val durationMillis: Long
        get() = if (startedAtMillis == 0L) 0L else System.currentTimeMillis() - startedAtMillis

    companion object {
        val EMPTY = ConnectionStats()
    }
}

/** Severity ladder used by the security scanner. */
enum class ThreatLevel(val severity: Int) {
    SAFE(0), LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

    val isActionable: Boolean get() = severity >= MEDIUM.severity
}

/** A single finding produced by the on-device security auditor. */
data class ThreatReport(
    val id: String,
    val title: String,
    val description: String,
    val recommendation: String,
    val level: ThreatLevel,
    val packageName: String? = null
)

/** Aggregate result of a full device scan. */
data class ScanResult(
    val threats: List<ThreatReport>,
    val appsScanned: Int,
    val filesScanned: Int,
    val durationMillis: Long,
    val rootDetected: Boolean,
    val completedAtMillis: Long
) {
    val overallLevel: ThreatLevel
        get() = threats.maxByOrNull { it.level.severity }?.level ?: ThreatLevel.SAFE
}

/** Profile of an installed app as consumed by the threat analyzer. */
data class AppProfile(
    val packageName: String,
    val label: String,
    val requestedPermissions: List<String>,
    val installerSource: InstallerSource,
    val targetSdk: Int,
    val usesDeviceAdmin: Boolean = false,
    val hasAccessibilityService: Boolean = false,
    val isSystemApp: Boolean = false
)

enum class InstallerSource { PLAY_STORE, SIDELOAD, SYSTEM, OEM_STORE }

/** App usage / traffic aggregation persisted by WorkManager. */
data class UsageSnapshot(
    val dayEpoch: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val connectedSeconds: Long
)
