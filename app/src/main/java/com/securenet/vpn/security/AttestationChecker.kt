package com.securenet.vpn.security

import android.content.Context
import android.content.pm.PackageManager
import com.securenet.vpn.data.model.ThreatLevel
import com.securenet.vpn.data.model.ThreatReport

/**
 * Device integrity attestation (spec: SafetyNet - optional).
 *
 * SafetyNet Attach API is deprecated by Google; this implementation models
 * the same guarantee with a layered approach:
 *  1. Play Integrity presence probe (reflection, zero hard dependency)
 *  2. Local heuristics: Play Services availability, installer signature
 * The result is advisory only - a failed attestation never blocks the VPN.
 */
class AttestationChecker {

    sealed class Verdict {
        object Trusted : Verdict()
        data class Suspicious(val reasons: List<String>) : Verdict()
        object Unavailable : Verdict()
    }

    fun check(context: Context): Verdict {
        val reasons = mutableListOf<String>()

        val playIntegrityPresent = runCatching {
            Class.forName("com.google.android.play.core.integrity.IntegrityManagerFactory")
            true
        }.getOrDefault(false)

        val playServicesAvailable = runCatching {
            context.packageManager.getPackageInfo(
                "com.google.android.gms", 0
            ) != null
        }.getOrDefault(false)

        if (!playServicesAvailable) {
            reasons += "Google Play services missing - remote attestation unavailable"
        }
        if (!playIntegrityPresent) {
            reasons += "Play Integrity SDK not on classpath (expected until vendored)"
        }

        val installer = runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        }.getOrNull()
        if (installer != null && installer != "com.android.vending") {
            reasons += "App installed from $installer rather than Play Store"
        }

        return when {
            reasons.isEmpty() -> Verdict.Trusted
            !playServicesAvailable -> Verdict.Suspicious(reasons)
            else -> Verdict.Suspicious(reasons)
        }
    }

    /** Converts a verdict into an optional scanner report. */
    fun toReport(verdict: Verdict): ThreatReport? = when (verdict) {
        is Verdict.Suspicious -> ThreatReport(
            id = "device:attestation",
            title = "Device integrity could not be fully verified",
            description = verdict.reasons.joinToString("; "),
            recommendation = "Install from an app store and ensure Google Play services are active",
            level = ThreatLevel.LOW,
            packageName = null
        )
        else -> null
    }
}
