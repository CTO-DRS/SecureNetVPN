package com.securenet.vpn.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.data.model.AppProfile
import com.securenet.vpn.data.model.InstallerSource
import com.securenet.vpn.data.model.ScanResult
import com.securenet.vpn.data.model.ThreatReport
import com.securenet.vpn.domain.repository.ScannerRepository
import com.securenet.vpn.intelligence.ThreatClassifier
import com.securenet.vpn.security.AppThreatAnalyzer
import com.securenet.vpn.security.FileThreatScanner
import com.securenet.vpn.security.RootDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device audit pipeline:
 *  1. app permission/behavior analysis (pure logic, unit-tested)
 *  2. on-device ML threat re-scoring (logistic-regression ThreatClassifier)
 *  3. known-bad file hash sweep of the user-visible storage
 *  4. root/tampering indicators
 * Nothing ever leaves the device; no network calls are involved.
 */
class ScannerRepositoryImpl(
    private val context: Context,
    private val securePrefs: SecurePrefs
) : ScannerRepository {

    private val threatAnalyzer = AppThreatAnalyzer()
    private val fileScanner = FileThreatScanner()
    private val rootDetector = RootDetector()
    private val classifier = ThreatClassifier()

    override suspend fun fullScan(): ScanResult = withContext(Dispatchers.Default) {
        val startedAt = System.currentTimeMillis()

        val appThreats = scanInstalledApps()
        val fileThreats = scanFiles()
        val rootThreats = rootDetector.detect(context)

        val threats = (appThreats + fileThreats + rootThreats)
            .sortedByDescending { it.level.severity }

        val result = ScanResult(
            threats = threats,
            appsScanned = installedAppProfiles().size,
            filesScanned = fileScanner.lastScannedFileCount,
            durationMillis = System.currentTimeMillis() - startedAt,
            rootDetected = rootThreats.isNotEmpty(),
            completedAtMillis = System.currentTimeMillis()
        )

        securePrefs.lastScanAt = result.completedAtMillis
        securePrefs.lastScanSummary = "${threats.size} findings"
        result
    }

    /**
     * Rule engine (AppThreatAnalyzer) + on-device ML (ThreatClassifier) run
     * side by side. The more severe of the two verdicts wins; when ML lifts
     * the severity it appends the top contributing factors so the user sees
     * WHY an app was flagged.
     */
    private fun scanInstalledApps(): List<ThreatReport> =
        installedAppProfiles().flatMap { profile ->
            val ruleReports = threatAnalyzer.analyze(profile)
            val prediction = classifier.predict(profile)

            val bestRule = ruleReports.maxByOrNull { it.level.severity }
            val mlStronger = bestRule == null ||
                prediction.level.severity > bestRule.level.severity

            if (!mlStronger) {
                ruleReports
            } else if (prediction.level.isActionable) {
                val factors = prediction.topFactors.joinToString(", ")
                listOf(
                    ThreatReport(
                        id = "ml_${profile.packageName}",
                        title = "ML: ${profile.label}",
                        description = buildString {
                            append("On-device model rated this app ")
                            append("%.0f%% risk".format(prediction.probability * 100))
                            if (factors.isNotEmpty()) append(". Signals: $factors")
                        },
                        recommendation = "Review this app's permissions or remove it if you do not recognize it.",
                        level = prediction.level,
                        packageName = profile.packageName
                    )
                ) + ruleReports
            } else {
                ruleReports
            }
        }

    private fun scanFiles(): List<ThreatReport> =
        fileScanner.scanPublicDirectories(context)

    private fun installedAppProfiles(): List<AppProfile> {
        val pm: PackageManager = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        return pm.getInstalledApplications(flags)
            .filter { it.packageName != context.packageName }
            .filter { !it.isSystemAppOrUpdatedSystem(pm) }
            .map { info ->
                val requested = try {
                    pm.getPackageInfo(info.packageName, PackageManager.GET_PERMISSIONS)
                        .requestedPermissions?.toList() ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
                AppProfile(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    requestedPermissions = requested,
                    installerSource = resolveInstaller(info.packageName, pm, info),
                    targetSdk = info.targetSdkVersion,
                    usesDeviceAdmin = hasDeviceAdmin(info.packageName, pm),
                    hasAccessibilityService = requested.any { it.contains("ACCESSIBILITY", true) },
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
    }

    private fun ApplicationInfo.isSystemAppOrUpdatedSystem(pm: PackageManager): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

    private fun resolveInstaller(
        packageName: String,
        pm: PackageManager,
        info: ApplicationInfo
    ): InstallerSource = try {
        val source = pm.getInstallSourceInfo(packageName)
        when (source.installingPackageName) {
            "com.android.vending", "com.google.android.feedback" -> InstallerSource.PLAY_STORE
            null -> InstallerSource.SIDELOAD
            else -> InstallerSource.SIDELOAD
        }
    } catch (_: Exception) {
        if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
            InstallerSource.SYSTEM
        } else {
            InstallerSource.SIDELOAD
        }
    }

    private fun hasDeviceAdmin(packageName: String, pm: PackageManager): Boolean = try {
        // Literal instead of the DevicePolicyManager constant: identical
        // value ("android.app.action.DEVICE_ADMIN_ENABLED"), stable across
        // compileSdk levels and JVM-friendly.
        val deviceAdmin = "android.app.action.DEVICE_ADMIN_ENABLED"
        pm.queryBroadcastReceivers(
            android.content.Intent(deviceAdmin).setPackage(packageName), 0
        ).size > 0
    } catch (_: Exception) {
        false
    }
}
