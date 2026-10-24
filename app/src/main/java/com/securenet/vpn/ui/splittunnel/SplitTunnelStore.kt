package com.securenet.vpn.ui.splittunnel

import org.json.JSONArray

/**
 * Pure model of the split-tunnel policy (no Android imports -> JVM-testable).
 *
 *   OFF        : every app uses the tunnel
 *   ALLOW_ONLY : ONLY the listed apps use the tunnel (whitelist)
 *   EXCLUDE    : every app EXCEPT the listed ones uses the tunnel (blacklist)
 */
object SplitTunnelStore {

    enum class Mode { OFF, ALLOW_ONLY, EXCLUDE }

    data class Policy(val mode: Mode, val packages: Set<String>)

    fun encode(policy: Policy): String {
        val arr = JSONArray()
        policy.packages.sorted().forEach { arr.put(it) }
        return arr.toString()
    }

    fun decode(rawPackages: String, modeInt: Int): Policy = Policy(
        mode = when (modeInt) {
            1 -> Mode.ALLOW_ONLY
            2 -> Mode.EXCLUDE
            else -> Mode.OFF
        },
        packages = runCatching {
            val arr = JSONArray(rawPackages)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    )

    /** Pure decision helper used by the service when building the TUN. */
    fun appliesTo(policy: Policy, packageName: String): Boolean = when (policy.mode) {
        Mode.OFF -> true
        Mode.ALLOW_ONLY -> packageName in policy.packages
        Mode.EXCLUDE -> packageName !in policy.packages
    }
}
