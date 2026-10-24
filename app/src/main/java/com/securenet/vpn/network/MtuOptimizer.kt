package com.securenet.vpn.network

/**
 * WireGuard MTU planner.
 *
 * Overhead model (outer headers added by the tunnel):
 *  - IPv4 outer: 20 (IP) + 8 (UDP) + 4 (type+reserved) + 4 (ridx) + 8 (counter) + 16 (Poly1305) = 60
 *  - IPv6 outer: 40 (IP) + 8 (UDP) + 32 (data-plane header + tag)          = 80
 * A 16-byte safety margin is applied because real path characteristics
 * (extra tunneling, PPPoE) frequently shave more.
 */
class MtuOptimizer {

    /**
     * Recommended tunnel MTU for a given link MTU.
     * WireGuard floors at 1280 (IPv6 minimum); caps at 1420 for safety.
     */
    fun recommendedMtu(linkMtu: Int = 1500, useIpv6: Boolean = false): Int {
        val overhead = (if (useIpv6) OVERHEAD_IPV6 else OVERHEAD_IPV4) + SAFETY_MARGIN
        val computed = linkMtu - overhead
        return computed.coerceIn(MIN_MTU, maxOf(MIN_MTU, minOf(linkMtu - MIN_OVERHEAD_ABSOLUTE, MAX_TUNNEL_MTU)))
    }

    /** Clamps a user/probe value into the valid WireGuard MTU range. */
    fun clamp(mtu: Int): Int = mtu.coerceIn(MIN_MTU, MAX_LINK_MTU)

    /**
     * One step of the binary-search path-MTU probe.
     * Stateless by design so callers can persist progress and unit tests can
     * drive the full convergence deterministically.
     *
     * @param probePacket the last probe result (null = not started)
     * @return the next payload size to probe, or the converged MTU when
     *         the search window is exhausted.
     */
    fun nextProbe(state: ProbeState, success: Boolean?): ProbeState {
        val lo = state.lowerBound
        val hi = state.upperBound

        // No probe yet: start in the middle of the window.
        if (success == null) {
            val mid = (lo + hi) / 2
            return state.copy(currentProbe = mid)
        }

        return if (success) {
            val nextLower = state.currentProbe
            val newLo = maxOf(lo, nextLower)
            if (hi - newLo <= CONVERGENCE_EPSILON) {
                state.copy(lowerBound = newLo, converged = true, currentProbe = newLo)
            } else {
                val mid = (newLo + hi) / 2
                state.copy(lowerBound = newLo, currentProbe = mid)
            }
        } else {
            val newHi = minOf(hi, state.currentProbe - 1)
            if (newHi <= lo) {
                state.copy(upperBound = newHi, converged = true, currentProbe = lo)
            } else {
                val mid = (lo + newHi) / 2
                state.copy(upperBound = newHi, currentProbe = mid)
            }
        }
    }

    /** Applies battery/platform caps: fragmented packets drain batteries. */
    fun applyPolicy(mtu: Int, batterySaver: Boolean): Int =
        if (batterySaver) minOf(mtu, BATTERY_SAVER_CAP_MTU) else mtu

    data class ProbeState(
        val lowerBound: Int = MIN_MTU,
        val upperBound: Int = MAX_TUNNEL_MTU,
        val currentProbe: Int = -1,
        val converged: Boolean = false
    )

    companion object {
        const val MIN_MTU = 1280
        const val MAX_TUNNEL_MTU = 1420
        const val MAX_LINK_MTU = 1500
        const val OVERHEAD_IPV4 = 60
        const val OVERHEAD_IPV6 = 80
        const val SAFETY_MARGIN = 16
        const val MIN_OVERHEAD_ABSOLUTE = 60
        const val BATTERY_SAVER_CAP_MTU = 1380
        const val CONVERGENCE_EPSILON = 8
    }
}
