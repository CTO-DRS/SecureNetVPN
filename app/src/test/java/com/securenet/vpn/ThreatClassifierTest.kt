package com.securenet.vpn

import com.securenet.vpn.data.model.AppProfile
import com.securenet.vpn.data.model.InstallerSource
import com.securenet.vpn.data.model.ThreatLevel
import com.securenet.vpn.intelligence.ThreatClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatClassifierTest {

    private val classifier = ThreatClassifier()

    private fun profile(
        permissions: List<String> = emptyList(),
        sideload: Boolean = false,
        deviceAdmin: Boolean = false,
        accessibility: Boolean = false,
        targetSdk: Int = 34
    ) = AppProfile(
        packageName = "com.test.app",
        label = "TestApp",
        requestedPermissions = permissions,
        installerSource = if (sideload) InstallerSource.SIDELOAD else InstallerSource.PLAY_STORE,
        targetSdk = targetSdk,
        usesDeviceAdmin = deviceAdmin,
        hasAccessibilityService = accessibility
    )

    @Test
    fun `clean play store app is SAFE`() {
        val p = profile(permissions = listOf("android.permission.INTERNET"))
        val prediction = classifier.predict(p)
        assertEquals(ThreatLevel.SAFE, prediction.level)
        assertTrue(prediction.probability < 0.15)
    }

    @Test
    fun `accessibility + sms + sideload is CRITICAL spyware pattern`() {
        val p = profile(
            permissions = listOf(
                "android.permission.RECEIVE_SMS",
                "android.permission.READ_SMS",
                "android.permission.RECORD_AUDIO",
                "android.permission.READ_CALL_LOG"
            ),
            sideload = true,
            accessibility = true
        )
        val prediction = classifier.predict(p)
        assertTrue("expected CRITICAL/HIGH got ${prediction.level}",
            prediction.level.severity >= ThreatLevel.HIGH.severity)
        assertTrue(prediction.topFactors.contains("Accessibility service"))
    }

    @Test
    fun `device admin alone reaches at least MEDIUM`() {
        val p = profile(deviceAdmin = true)
        val prediction = classifier.predict(p)
        assertTrue("expected >= MEDIUM got ${prediction.level}",
            prediction.level.severity >= ThreatLevel.MEDIUM.severity)
    }

    @Test
    fun `feature vector is aligned with weights`() {
        val features = classifier.extract(profile())
        assertEquals(ThreatClassifier.WEIGHTS.size, features.toVector().size)
    }

    @Test
    fun `probability stays within 0 and 1 for extreme inputs`() {
        val p = profile(
            permissions = List(60) { "android.permission.SMS_$it" } +
                listOf("android.permission.SEND_SMS", "android.permission.CAMERA"),
            sideload = true, deviceAdmin = true, accessibility = true, targetSdk = 22
        )
        val prediction = classifier.predict(p)
        assertTrue(prediction.probability in 0.0..1.0)
    }

    @Test
    fun `stale target sdk raises risk`() {
        val modern = classifier.predict(profile(sideload = true, targetSdk = 34))
        val stale = classifier.predict(profile(sideload = true, targetSdk = 28))
        assertTrue(stale.probability > modern.probability)
    }
}
