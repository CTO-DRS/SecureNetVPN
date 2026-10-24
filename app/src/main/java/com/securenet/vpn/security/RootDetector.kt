package com.securenet.vpn.security

import android.content.Context
import com.securenet.vpn.data.model.ThreatLevel
import com.securenet.vpn.data.model.ThreatReport
import java.io.File

/**
 * Root / tampering indicators. The pure classification runs on JVM tests;
 * the Android path checks well-known su binaries, Magisk artifacts and
 * build-tag tampering.
 */
class RootDetector {

    /** Checks su/Magisk presence on the filesystem (fast, non-destructive). */
    fun detect(context: Context): List<ThreatReport> {
        val foundBinaries = KNOWN_SU_PATHS.filter { path ->
            runCatching { File(path).exists() }.getOrDefault(false)
        }
        val magiskFound = MAGISK_INDICATORS.any { path ->
            runCatching { File(path).exists() }.getOrDefault(false)
        }
        val buildTagTampered = android.os.Build.TAGS?.contains("test-keys") == true

        return buildReports(context.packageName, foundBinaries, magiskFound, buildTagTampered)
    }

    /** Pure decision logic (unit-tested). */
    fun buildReports(
        appPackageName: String,
        suPathsFound: List<String>,
        magiskArtifactsFound: Boolean,
        buildTagsTampered: Boolean
    ): List<ThreatReport> {
        val flags = suPathsFound.isNotEmpty() || magiskArtifactsFound || buildTagsTampered
        if (!flags) return emptyList()

        val reasons = mutableListOf<String>()
        if (suPathsFound.isNotEmpty()) reasons += "su binary found at ${suPathsFound.first()}"
        if (magiskArtifactsFound) reasons += "Magisk root artifacts present"
        if (buildTagsTampered) reasons += "Build is signed with test keys"

        return listOf(
            ThreatReport(
                id = "device:root",
                title = "Root access detected",
                description = reasons.joinToString("; "),
                recommendation = "Rooted devices weaken app sandboxing; VPN keys are still " +
                    "protected by hardware keystore, but treat installed apps with care",
                level = ThreatLevel.MEDIUM,
                packageName = null
            )
        )
    }

    companion object {
        val KNOWN_SU_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/vendor/bin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )

        val MAGISK_INDICATORS = listOf(
            "/data/adb/magisk",
            "/cache/magisk.log",
            "/data/adb/magisk/busybox"
        )
    }
}
