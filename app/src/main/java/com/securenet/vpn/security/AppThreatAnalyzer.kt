package com.securenet.vpn.security

import com.securenet.vpn.data.model.AppProfile
import com.securenet.vpn.data.model.InstallerSource
import com.securenet.vpn.data.model.ThreatLevel
import com.securenet.vpn.data.model.ThreatReport

/**
 * Pure risk-scoring engine for installed apps. Weighted permission analysis
 * plus behavioral flags produce graded, explainable findings. No Android
 * imports: fully unit-testable on the JVM.
 */
class AppThreatAnalyzer {

    fun analyze(profile: AppProfile): List<ThreatReport> {
        val score = score(profile)
        return buildReports(profile, score)
    }

    /** Total weighted risk score 0..100. */
    fun score(profile: AppProfile): Int {
        var score = 0

        profile.requestedPermissions.forEach { perm ->
            score += permissionWeight(perm)
        }

        if (profile.usesDeviceAdmin) score += WEIGHT_DEVICE_ADMIN
        if (profile.hasAccessibilityService) score += WEIGHT_ACCESSIBILITY
        if (profile.installerSource == InstallerSource.SIDELOAD) score += WEIGHT_SIDELOAD
        if (profile.targetSdk in 1 until MIN_TRUSTED_TARGET_SDK) score += WEIGHT_OLD_TARGET_SDK
        if (profile.isSystemApp) score /= 2

        return score.coerceIn(0, 100)
    }

    private fun buildReports(profile: AppProfile, score: Int): List<ThreatReport> {
        val level = levelFor(score)
        if (level == ThreatLevel.SAFE) return emptyList()

        val reasons = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        val risky = profile.requestedPermissions
            .mapNotNull { p -> permissionWeight(p).takeIf { it >= HIGH_RISK_PERMISSION_THRESHOLD }?.let { p to it } }
            .sortedByDescending { it.second }
        if (risky.isNotEmpty()) {
            reasons += "Requests high-risk permissions: " +
                risky.take(3).joinToString { (p, _) -> p.substringAfterLast('.') }
            recommendations += "Review why ${profile.label} needs these permissions"
        }

        if (profile.usesDeviceAdmin) {
            reasons += "Holds device-admin rights (hard to uninstall)"
            recommendations += "Check device admin apps in Settings > Security"
        }
        if (profile.hasAccessibilityService) {
            reasons += "Uses accessibility services (can read screen content)"
            recommendations += "Disable accessibility access unless explicitly needed"
        }
        if (profile.installerSource == InstallerSource.SIDELOAD) {
            reasons += "Installed outside an app store (sideloaded APK)"
            recommendations += "Verify the APK source and signature"
        }
        if (profile.targetSdk in 1 until MIN_TRUSTED_TARGET_SDK) {
            reasons += "Targets an outdated Android API (${profile.targetSdk})"
            recommendations += "Prefer apps kept up to date by the developer"
        }

        return listOf(
            ThreatReport(
                id = "app:${profile.packageName}",
                title = "${profile.label} - ${gradeLabel(level)}",
                description = reasons.joinToString("; "),
                recommendation = recommendations.joinToString(". "),
                level = level,
                packageName = profile.packageName
            )
        )
    }

    private fun gradeLabel(level: ThreatLevel): String = when (level) {
        ThreatLevel.MEDIUM -> "medium risk"
        ThreatLevel.HIGH -> "high risk"
        ThreatLevel.CRITICAL -> "critical risk"
        else -> "low risk"
    }

    fun levelFor(score: Int): ThreatLevel = when {
        score >= THRESHOLD_CRITICAL -> ThreatLevel.CRITICAL
        score >= THRESHOLD_HIGH -> ThreatLevel.HIGH
        score >= THRESHOLD_MEDIUM -> ThreatLevel.MEDIUM
        score >= THRESHOLD_LOW -> ThreatLevel.LOW
        else -> ThreatLevel.SAFE
    }

    private fun permissionWeight(permission: String): Int {
        val p = permission.substringAfterLast('.')
        return when {
            p.contains("SEND_SMS") -> 30
            p.contains("READ_SMS") || p.contains("RECEIVE_SMS") -> 25
            p.contains("RECORD_AUDIO") -> 20
            p.contains("CALL_PHONE") || p.contains("PROCESS_OUTGOING_CALLS") -> 25
            p.contains("READ_CALL_LOG") -> 20
            p.contains("READ_CONTACTS") || p.contains("WRITE_CONTACTS") -> 15
            p.contains("ACCESS_FINE_LOCATION") -> 12
            p.contains("CAMERA") -> 12
            p.contains("QUERY_ALL_PACKAGES") -> 18
            p.contains("SYSTEM_ALERT_WINDOW") -> 12
            p.contains("READ_EXTERNAL_STORAGE") || p.contains("READ_MEDIA_") -> 8
            p.contains("REQUEST_INSTALL_PACKAGES") -> 25
            p.contains("PACKAGE_USAGE_STATS") -> 10
            else -> 2
        }
    }

    companion object {
        // 26 so that device-admin plus any single mid-weight permission
        // (e.g. CAMERA = 12) reaches the MEDIUM risk band (>= 38).
        const val WEIGHT_DEVICE_ADMIN = 26
        const val WEIGHT_ACCESSIBILITY = 20
        const val WEIGHT_SIDELOAD = 15
        const val WEIGHT_OLD_TARGET_SDK = 12
        const val MIN_TRUSTED_TARGET_SDK = 29
        const val HIGH_RISK_PERMISSION_THRESHOLD = 15

        const val THRESHOLD_LOW = 18
        const val THRESHOLD_MEDIUM = 38
        const val THRESHOLD_HIGH = 62
        const val THRESHOLD_CRITICAL = 82
    }
}
