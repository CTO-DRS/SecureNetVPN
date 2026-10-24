package com.securenet.vpn

import com.securenet.vpn.intelligence.SmartInsightsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartInsightsEngineTest {

    private val engine = SmartInsightsEngine()

    private fun inputs(
        killSwitch: Boolean = false,
        shield: Boolean = false,
        smartRules: Boolean = false,
        autoLaunch: Boolean = false,
        hasScan: Boolean = false,
        scanAge: Long? = null,
        threats: Int = 0
    ) = SmartInsightsEngine.Inputs(
        killSwitchEnabled = killSwitch,
        dnsShieldEnabled = shield,
        smartRulesEnabled = smartRules,
        autoConnectOnLaunch = autoLaunch,
        hasScanResults = hasScan,
        scanAgeMillis = scanAge,
        openThreats = threats,
        connected = false,
        batterySaver = false
    )

    @Test
    fun `clean profile yields no insights`() {
        val out = engine.generate(
            inputs(killSwitch = true, shield = true, smartRules = true, autoLaunch = true, hasScan = true, scanAge = 0L)
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `open threats rank first as HIGH`() {
        val out = engine.generate(inputs(threats = 2, hasScan = true, scanAge = 0L))
        assertEquals("threats", out.first().key)
        assertEquals(SmartInsightsEngine.Severity.HIGH, out.first().severity)
    }

    @Test
    fun `kill switch missing is HIGH severity`() {
        val out = engine.generate(inputs())
        assertTrue(out.any { it.key == "kill_switch" && it.severity == SmartInsightsEngine.Severity.HIGH })
    }

    @Test
    fun `dns shield missing is MEDIUM severity`() {
        val out = engine.generate(inputs(killSwitch = true))
        assertTrue(out.any { it.key == "dns_shield" && it.severity == SmartInsightsEngine.Severity.MEDIUM })
    }

    @Test
    fun `never scanned flags scan_missing`() {
        val out = engine.generate(inputs(killSwitch = true))
        assertTrue(out.any { it.key == "scan_missing" })
    }

    @Test
    fun `stale scan flags scan_stale`() {
        val out = engine.generate(
            inputs(killSwitch = true, hasScan = true, scanAge = SmartInsightsEngine.STALE_SCAN_MS + 1)
        )
        assertTrue(out.any { it.key == "scan_stale" })
    }

    @Test
    fun `fresh scan does not flag`() {
        val out = engine.generate(
            inputs(killSwitch = true, shield = true, smartRules = true, autoLaunch = true, hasScan = true, scanAge = 3_600_000L)
        )
        assertTrue(out.none { it.key == "scan_stale" || it.key == "scan_missing" })
    }

    @Test
    fun `output capped and severity-ordered`() {
        val out = engine.generate(inputs(threats = 1))
        assertTrue(out.size <= 4)
        assertEquals(out.sortedBy { it.severity.ordinal }, out)
    }
}
