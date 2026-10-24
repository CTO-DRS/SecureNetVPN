package com.securenet.vpn.multihop

import com.securenet.vpn.data.model.VpnServer
import org.json.JSONObject

/**
 * v1.4: Multi-hop (server chain). The user picks an ENTRY relay and an EXIT
 * server; traffic path becomes device → entry (relay) → exit → internet.
 *
 * The chain is persisted fully self-contained (both servers serialized as
 * JSON) so every consumer — the live connect path, the cached-config
 * reconnect path in the service, the boot receiver — can rebuild the plan
 * without touching the server repository.
 */
data class MultiHopChain(
    val enabled: Boolean = false,
    val entryJson: String = "",
    val exitJson: String = ""
) {

    fun entryServer(): VpnServer? =
        runCatching { VpnServer.fromVpnServerJson(entryJson) }.getOrNull()

    fun exitServer(): VpnServer? =
        runCatching { VpnServer.fromVpnServerJson(exitJson) }.getOrNull()

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled)
        put("entry", entryJson)
        put("exit", exitJson)
    }.toString()

    companion object {
        fun fromJson(raw: String?): MultiHopChain = runCatching {
            val o = JSONObject(raw ?: return disabled())
            MultiHopChain(
                enabled = o.optBoolean("enabled", false),
                entryJson = o.optString("entry"),
                exitJson = o.optString("exit")
            )
        }.getOrDefault(disabled())

        fun disabled() = MultiHopChain()
    }
}

/**
 * Pure multi-hop decision logic — no Android imports, fully unit-tested.
 */
object MultiHopEngine {

    /**
     * Extra encapsulation overhead a nested hop costs (outer WireGuard
     * header ~60B + inner IP/UDP ~20B, rounded up). The inner tunnel must
     * shrink its MTU by this much or outer packets fragment and stall.
     */
    const val HOP_OVERHEAD_BYTES = 80

    enum class Validation { OK, MISSING_ENTRY, MISSING_EXIT, SAME_SERVER }

    data class ChainPlan(
        /** Entry server acts as the transport relay for the tunnel. */
        val relayHost: String,
        val relayPort: Int,
        /** Exit server owns the WireGuard peer key and DNS resolvers. */
        val exit: VpnServer,
        val innerMtu: Int,
        /** Human-readable path label, e.g. "Warsaw → Zurich". */
        val label: String
    )

    fun validate(entry: VpnServer?, exit: VpnServer?): Validation = when {
        entry == null -> Validation.MISSING_ENTRY
        exit == null -> Validation.MISSING_EXIT
        entry.id == exit.id -> Validation.SAME_SERVER
        else -> Validation.OK
    }

    fun innerMtu(outerMtu: Int = 1420): Int =
        (outerMtu - HOP_OVERHEAD_BYTES).coerceAtLeast(1200)

    /**
     * Builds the concrete connection plan for a valid chain, or null.
     *
     * Relay model: the WireGuard transport endpoint points at the ENTRY
     * server while the peer key/DNS belong to the EXIT server — the entry
     * hop forwards the encrypted inner session, the exit hop decrypts.
     * Requires relay-capable server fleet config (entry forwards UDP for
     * the exit's endpoint); the plan is what the fleet must honor.
     */
    fun plan(entry: VpnServer?, exit: VpnServer?, outerMtu: Int = 1420): ChainPlan? {
        if (validate(entry, exit) != Validation.OK) return null
        return ChainPlan(
            relayHost = entry!!.endpointHost,
            relayPort = entry.endpointPort,
            exit = exit!!,
            innerMtu = innerMtu(outerMtu),
            label = "${entry.city} → ${exit.city}"
        )
    }

    /** Settings-row summary: "off", "Warsaw → Zurich" or "incomplete". */
    fun describe(chain: MultiHopChain): String {
        if (!chain.enabled) return "off"
        val entry = chain.entryServer()
        val exit = chain.exitServer()
        return if (validate(entry, exit) == Validation.OK) {
            "${entry!!.city} → ${exit!!.city}"
        } else {
            "incomplete"
        }
    }
}
