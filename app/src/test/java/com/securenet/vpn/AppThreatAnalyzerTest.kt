package com.securenet.vpn

import com.securenet.vpn.data.model.AppProfile
import com.securenet.vpn.data.model.InstallerSource
import com.securenet.vpn.security.AppThreatAnalyzer
import com.securenet.vpn.security.RootDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThreatAnalyzerTest {

    private val analyzer = AppThreatAnalyzer()

    private fun profile(
        packageName: String = "com.example.app",
        permissions: List<String> = emptyList(),
        installer: InstallerSource = InstallerSource.PLAY_STORE,
        targetSdk: Int = 34,
        deviceAdmin: Boolean = false,
        accessibility: Boolean = false,
        system: Boolean = false
    ) = AppProfile(
        packageName = packageName,
        label = packageName.substringAfterLast('.'),
        requestedPermissions = permissions,
        installerSource = installer,
        targetSdk = targetSdk,
        usesDeviceAdmin = deviceAdmin,
        hasAccessibilityService = accessibility,
        isSystemApp = system
    )

    @Test
    fun `clean store app scores safe`() {
        val score = analyzer.score(profile())
        assertEquals(0, score)
        assertTrue(analyzer.analyze(profile()).isEmpty())
    }

    @Test
    fun `sms package with sideload crosses high threshold`() {
        val p = profile(
            permissions = listOf(
                "android.permission.SEND_SMS",
                "android.permission.READ_SMS",
                "android.permission.READ_CONTACTS"
            ),
            installer = InstallerSource.SIDELOAD
        )
        val score = analyzer.score(p)
        assertTrue("expected high score, got $score", score >= AppThreatAnalyzer.THRESHOLD_HIGH)
        assertTrue(analyzer.levelFor(score) in listOf(
            com.securenet.vpn.data.model.ThreatLevel.HIGH,
            com.securenet.vpn.data.model.ThreatLevel.CRITICAL
        ))
    }

    @Test
    fun `accessibility abuse adds weight`() {
        val base = analyzer.score(profile())
        val withA11y = analyzer.score(profile(accessibility = true))
        assertEquals(base + AppThreatAnalyzer.WEIGHT_ACCESSIBILITY, withA11y)
    }

    @Test
    fun `device admin flags as risky`() {
        val p = profile(
            permissions = listOf("android.permission.CAMERA"),
            deviceAdmin = true
        )
        val score = analyzer.score(p)
        assertTrue(score >= AppThreatAnalyzer.THRESHOLD_MEDIUM)
        val report = analyzer.analyze(p).first()
        assertTrue(report.description.contains("device-admin"))
    }

    @Test
    fun `old target sdk increases score`() {
        val modern = analyzer.score(profile())
        val old = analyzer.score(profile(targetSdk = 23))
        assertEquals(modern + AppThreatAnalyzer.WEIGHT_OLD_TARGET_SDK, old)
    }

    @Test
    fun `system app risk is dampened by half`() {
        val risky = profile(
            permissions = listOf("android.permission.RECORD_AUDIO"),
            system = true
        )
        val notSystem = profile(permissions = listOf("android.permission.RECORD_AUDIO"))
        assertTrue(analyzer.score(risky) < analyzer.score(notSystem))
    }

    @Test
    fun `score is clamped to 100`() {
        val p = profile(
            permissions = listOf(
                "android.permission.SEND_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.RECORD_AUDIO",
                "android.permission.CALL_PHONE",
                "android.permission.READ_CALL_LOG",
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.CAMERA",
                "android.permission.QUERY_ALL_PACKAGES",
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.PACKAGE_USAGE_STATS"
            ),
            installer = InstallerSource.SIDELOAD,
            targetSdk = 22,
            deviceAdmin = true,
            accessibility = true
        )
        assertTrue(analyzer.score(p) <= 100)
        assertEquals(
            com.securenet.vpn.data.model.ThreatLevel.CRITICAL,
            analyzer.levelFor(analyzer.score(p))
        )
    }
}

class RootDetectorLogicTest {

    private val detector = RootDetector()

    @Test
    fun `no indicators produce no reports`() {
        val reports = detector.buildReports(
            appPackageName = "com.securenet.vpn",
            suPathsFound = emptyList(),
            magiskArtifactsFound = false,
            buildTagsTampered = false
        )
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `su binary produces a medium report`() {
        val reports = detector.buildReports(
            appPackageName = "com.securenet.vpn",
            suPathsFound = listOf("/system/bin/su"),
            magiskArtifactsFound = false,
            buildTagsTampered = false
        )
        assertEquals(1, reports.size)
        assertFalse(reports[0].level.isActionable.not()) // MEDIUM is actionable
    }

    @Test
    fun `magisk plus test keys combine reasons`() {
        val reports = detector.buildReports(
            appPackageName = "com.securenet.vpn",
            suPathsFound = emptyList(),
            magiskArtifactsFound = true,
            buildTagsTampered = true
        )
        assertEquals(1, reports.size)
        assertTrue(reports[0].description.contains("Magisk"))
        assertTrue(reports[0].description.contains("test keys"))
    }
}
