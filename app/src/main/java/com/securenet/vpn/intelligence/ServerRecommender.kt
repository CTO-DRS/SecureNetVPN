package com.securenet.vpn.intelligence

import com.securenet.vpn.data.model.VpnServer
import kotlin.math.abs

/**
 * Smart Connect: scores candidate servers fully on-device and picks the one
 * most likely to feel fast right now. The score blends three signals:
 *
 *   - live latency (measured by [ServerLatencyProber], falls back to the
 *     server's reported latency, then to a neutral penalty)
 *   - current load percentage reported by the endpoint
 *   - a small stickiness bonus for the server the user last used, so the
 *     recommender does not bounce the user around on noise
 *
 * Pure function -> trivially unit-testable.
 */
class ServerRecommender(
    private val stickinessBonus: Double = 0.35,
    /** Servers scoring within this margin of the best are considered ties. */
    private val tieMargin: Double = 0.05
) {

    data class ScoredServer(
        val server: VpnServer,
        val score: Double,
        val latencyComponent: Double,
        val loadComponent: Double,
        val sticky: Boolean
    )

    fun score(
        server: VpnServer,
        lastUsedId: String? = null,
        liveLatencyMs: Int? = null
    ): Double {
        val latency = (liveLatencyMs ?: server.latencyMs.takeIf { it > 0 }
            ?: UNKNOWN_LATENCY).toDouble()
        val latencyComponent = 1.0 / (1.0 + latency / 120.0)          // 0..1, higher is better
        val loadComponent = 1.0 - (server.loadPercent.coerceIn(0, 100) / 100.0)
        val sticky = server.id == lastUsedId
        return 0.60 * latencyComponent + 0.40 * loadComponent +
            (if (sticky) stickinessBonus else 0.0)
    }

    fun recommend(
        servers: List<VpnServer>,
        lastUsedId: String? = null,
        liveLatencies: Map<String, Int> = emptyMap()
    ): ScoredServer? {
        if (servers.isEmpty()) return null
        val scored = servers.map { s ->
            val latency = liveLatencies[s.id] ?: s.latencyMs.takeIf { it > 0 }
            ScoredServer(
                server = s,
                score = score(s, lastUsedId, latency),
                latencyComponent = 1.0 / (1.0 + (latency ?: UNKNOWN_LATENCY.toInt()) / 120.0),
                loadComponent = 1.0 - (s.loadPercent.coerceIn(0, 100) / 100.0),
                sticky = s.id == lastUsedId
            )
        }.sortedByDescending { it.score }
        return scored.first()
    }

    /**
     * True when the currently selected server is still a sensible choice;
     * used to decide whether Smart Connect should suggest a switch.
     */
    fun shouldSuggestSwitch(
        current: VpnServer,
        candidates: List<VpnServer>,
        lastUsedId: String? = current.id,
        liveLatencies: Map<String, Int> = emptyMap()
    ): Boolean {
        val best = recommend(candidates, lastUsedId, liveLatencies) ?: return false
        if (best.server.id == current.id) return false
        val currentScore = score(current, lastUsedId,
            liveLatencies[current.id] ?: current.latencyMs.takeIf { it > 0 })
        return abs(best.score - currentScore) > tieMargin
    }

    private companion object {
        /** Neutral penalty when nothing knows the latency yet. */
        const val UNKNOWN_LATENCY = 220
    }
}
