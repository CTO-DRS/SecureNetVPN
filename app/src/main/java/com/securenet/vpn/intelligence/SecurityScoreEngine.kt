package com.securenet.vpn.intelligence

import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.ScanResult
import com.securenet.vpn.data.model.ThreatLevel

/**
 * Composite, fully local "device protection score" (0-100).
 *
 * Blends four weighted pillars into one number the dashboard can show:
 *
 *   - threat posture from the latest device scan        (weight 45)
 *   - tunnel state (connected = biggest single win)     (weight 30)
 *   - privacy hygiene of the settings                    (weight 15)
 *   - network-optimizer health (DNS cache / auto MTU)    (weight 10)
 *
 * Also produces a short, prioritized, localized-by-string-res action list —
 * the "what should I do next" intelligence for the dashboard card.
 */
class SecurityScoreEngine {

    data class SecurityScore(
        val total: Int,
        val grade: Grade,
        val pillarThreat: Int,
        val pillarTunnel: Int,
        val pillarHygiene: Int,
        val pillarOptimizer: Int
    )

    enum class Grade(val label: String) {
        EXCELLENT("A"), GOOD("B"), FAIR("C"), POOR("D"), CRITICAL("F");

        companion object {
            fun from(total: Int): Grade = when {
                total >= 85 -> EXCELLENT
                total >= 70 -> GOOD
                total >= 55 -> FAIR
                total >= 35 -> POOR
                else -> CRITICAL
            }
        }
    }

    /** Machine-readable recommendation keys; UI maps them to localized strings. */
    enum class Action {
        RUN_DEVICE_SCAN,
        UNINSTALL_HIGH_RISK_APPS,
        CONNECT_VPN,
        ENABLE_DNS_CACHE,
        ENABLE_AUTO_MTU,
        ENABLE_AUTO_RECONNECT,
        REVIEW_SIDeloaded_APPS
    }

    data class ScoreReport(
        val score: SecurityScore,
        val actions: List<Action>
    )

    fun compute(
        lastScan: ScanResult?,
        connectionState: ConnectionState,
        dnsCacheEnabled: Boolean,
        autoMtuEnabled: Boolean,
        autoReconnectEnabled: Boolean,
        batterySaverEnabled: Boolean = false
    ): ScoreReport {
        // ---- Pillar 1: threat posture (45) --------------------------------
        val threatPillar: Int = when {
            lastScan == null -> 18                       // never scanned: blind spot
            else -> {
                val critical = lastScan.threats.count { it.level == ThreatLevel.CRITICAL }
                val high = lastScan.threats.count { it.level == ThreatLevel.HIGH }
                val medium = lastScan.threats.count { it.level == ThreatLevel.MEDIUM }
                val base = 45 - critical * 12 - high * 7 - medium * 3
                // Stale scans decay: full credit only within 7 days.
                val ageDays =
                    (System.currentTimeMillis() - lastScan.completedAtMillis) / 86_400_000L
                val decay = when {
                    ageDays <= 7 -> 0
                    ageDays <= 30 -> 6
                    else -> 12
                }
                (base - decay).coerceIn(0, 45)
            }
        }

        // ---- Pillar 2: tunnel state (30) ----------------------------------
        val tunnelPillar: Int = when (connectionState) {
            ConnectionState.CONNECTED -> 30
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING,
            ConnectionState.REQUESTING_PERMISSION -> 18
            ConnectionState.DISCONNECTING -> 10
            else -> 0
        }

        // ---- Pillar 3: settings hygiene (15) ------------------------------
        var hygiene = 0
        if (dnsCacheEnabled) hygiene += 5
        if (autoMtuEnabled) hygiene += 4
        if (autoReconnectEnabled) hygiene += 4
        if (!batterySaverEnabled) hygiene += 2          // full-rate stats = accurate
        else hygiene += 1

        // ---- Pillar 4: optimizer health (10) -------------------------------
        val optimizerPillar: Int =
            ((if (dnsCacheEnabled) 6 else 0) + (if (autoMtuEnabled) 4 else 0))

        val total = (threatPillar + tunnelPillar + hygiene + optimizerPillar)
            .coerceIn(0, 100)

        // ---- Prioritized, data-driven recommendations ----------------------
        val actions = ArrayList<Action>()
        if (lastScan == null) actions += Action.RUN_DEVICE_SCAN
        if (lastScan?.threats?.any {
                it.level.severity >= ThreatLevel.HIGH.severity
            } == true) actions += Action.UNINSTALL_HIGH_RISK_APPS
        if (connectionState != ConnectionState.CONNECTED) actions += Action.CONNECT_VPN
        if (!dnsCacheEnabled) actions += Action.ENABLE_DNS_CACHE
        if (!autoMtuEnabled) actions += Action.ENABLE_AUTO_MTU
        if (!autoReconnectEnabled) actions += Action.ENABLE_AUTO_RECONNECT

        return ScoreReport(
            SecurityScore(
                total = total,
                grade = Grade.from(total),
                pillarThreat = threatPillar,
                pillarTunnel = tunnelPillar,
                pillarHygiene = hygiene,
                pillarOptimizer = optimizerPillar
            ),
            actions
        )
    }
}
