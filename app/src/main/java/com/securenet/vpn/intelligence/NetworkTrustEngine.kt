package com.securenet.vpn.intelligence

/**
 * v1.2 — Smart Network Rules: decides whether the tunnel should be brought
 * up automatically when the device lands on a network, based on a
 * user-managed trust list.
 *
 * Pure Kotlin (no Android imports) so the whole policy is unit-testable.
 * Fingerprints are deliberately coarse — transport type + metered flag —
 * because reading the SSID would require location permission; this keeps
 * the feature useful *and* privacy-friendly.
 */
class NetworkTrustEngine {

    /** Coarse network identity: "wifi"/"cellular"/"vpn"/"other"/"none". */
    data class Fingerprint(val transport: String, val metered: Boolean)

    enum class Action { CONNECT, NONE }

    /**
     * @param smartRulesEnabled user master toggle
     * @param connected        tunnel already up — never re-trigger
     * @param hasInternet      no point auto-connecting with no connectivity
     */
    fun decide(
        fingerprint: Fingerprint,
        trusted: List<Fingerprint>,
        smartRulesEnabled: Boolean,
        connected: Boolean,
        hasInternet: Boolean
    ): Action {
        if (!smartRulesEnabled || connected || !hasInternet) return Action.NONE
        if (fingerprint.transport == "wifi" && trusted.contains(fingerprint)) {
            return Action.NONE // trusted Wi-Fi — user said it's safe
        }
        // Untrusted Wi-Fi and any metered cellular data network are treated
        // as hostile territory: protection comes first.
        return Action.CONNECT
    }

    // ------------------------------------------------------------------
    // Persistence helpers (tiny hand-rolled JSON — unit-test friendly)
    // Format: [{"t":"wifi","m":false}, ...]
    // ------------------------------------------------------------------

    fun trustListToJson(trusted: List<Fingerprint>): String =
        trusted.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ","
        ) { fp -> "{\"t\":\"${fp.transport}\",\"m\":${fp.metered}}" }

    fun trustListFromJson(raw: String?): List<Fingerprint> {
        if (raw.isNullOrBlank()) return emptyList()
        val trimmed = raw.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        return trimmed.drop(1).dropLast(1)
            .split("},{")
            .mapNotNull { chunk ->
                val t = chunk.substringAfter("\"t\":\"", "").substringBefore("\"")
                val m = chunk.substringAfter("\"m\":", "").substringBefore("}")
                if (t.isEmpty()) null
                else Fingerprint(t, m == "true")
            }
    }
}
