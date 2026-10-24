package com.securenet.vpn.intelligence

/**
 * v1.2 — Smart Insights: merges every protection signal the app already
 * computes (kill switch, DNS shield, scan freshness, smart rules…) into a
 * short, prioritized, fully-local list of actionable recommendations.
 *
 * The output is a list of stable string KEYS; the UI layer maps each key to
 * a localized resource so this engine stays language-agnostic.
 */
class SmartInsightsEngine {

    enum class Severity { HIGH, MEDIUM, LOW }

    data class Insight(val key: String, val severity: Severity)

    data class Inputs(
        val killSwitchEnabled: Boolean,
        val dnsShieldEnabled: Boolean,
        val smartRulesEnabled: Boolean,
        val autoConnectOnLaunch: Boolean,
        val hasScanResults: Boolean,
        /** Millis since last scan; null when never scanned. */
        val scanAgeMillis: Long?,
        val openThreats: Int,
        val connected: Boolean,
        val batterySaver: Boolean
    )

    /** Keys returned: kill_switch, dns_shield, threats, scan_stale, scan_missing, smart_rules, auto_launch */
    fun generate(inputs: Inputs): List<Insight> {
        val out = mutableListOf<Insight>()

        if (inputs.openThreats > 0) {
            out += Insight("threats", Severity.HIGH)
        }
        if (!inputs.killSwitchEnabled) {
            out += Insight("kill_switch", Severity.HIGH)
        }
        if (!inputs.dnsShieldEnabled) {
            out += Insight("dns_shield", Severity.MEDIUM)
        }
        when {
            !inputs.hasScanResults ->
                out += Insight("scan_missing", Severity.MEDIUM)
            inputs.scanAgeMillis != null && inputs.scanAgeMillis > STALE_SCAN_MS ->
                out += Insight("scan_stale", Severity.LOW)
        }
        if (!inputs.smartRulesEnabled) {
            out += Insight("smart_rules", Severity.LOW)
        }
        if (!inputs.autoConnectOnLaunch) {
            out += Insight("auto_launch", Severity.LOW)
        }

        return out.sortedBy { it.severity.ordinal }.take(MAX_INSIGHTS)
    }

    companion object {
        private const val MAX_INSIGHTS = 4
        const val STALE_SCAN_MS: Long = 7L * 24 * 60 * 60 * 1000 // one week
    }
}
