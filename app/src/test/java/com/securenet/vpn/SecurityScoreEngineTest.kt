package com.securenet.vpn

import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.ScanResult
import com.securenet.vpn.data.model.ThreatLevel
import com.securenet.vpn.data.model.ThreatReport
import com.securenet.vpn.intelligence.SecurityScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityScoreEngineTest {

    private val engine = SecurityScoreEngine()

    private fun scan(threats: List<ThreatReport>, ageDays: Long = 0) = ScanResult(
        threats = threats,
        appsScanned = 42,
        filesScanned = 100,
        durationMillis = 500,
        rootDetected = false,
        completedAtMillis = System.currentTimeMillis() - ageDays * 86_400_000L
    )

    private fun threat(level: ThreatLevel) = ThreatReport(
        id = level.name, title = "t", description = "d", recommendation = "r", level = level
    )

    @Test
    fun `never-scanned device scores low and asks for a scan`() {
        val report = engine.compute(
            lastScan = null,
            connectionState = ConnectionState.DISCONNECTED,
            dnsCacheEnabled = true, autoMtuEnabled = true, autoReconnectEnabled = true
        )
        assertTrue(report.score.total < 55)
        assertEquals(SecurityScoreEngine.Action.RUN_DEVICE_SCAN, report.actions.first())
    }

    @Test
    fun `connected clean optimized device scores excellent`() {
        val report = engine.compute(
            lastScan = scan(emptyList()),
            connectionState = ConnectionState.CONNECTED,
            dnsCacheEnabled = true, autoMtuEnabled = true, autoReconnectEnabled = true
        )
        assertTrue("got ${report.score.total}", report.score.total >= 85)
        assertEquals(SecurityScoreEngine.Grade.EXCELLENT, report.score.grade)
        assertTrue(report.actions.isEmpty())
    }

    @Test
    fun `critical threats drag the score down hard`() {
        val clean = engine.compute(
            scan(emptyList()), ConnectionState.DISCONNECTED, true, true, true
        ).score.total
        val dirty = engine.compute(
            scan(listOf(threat(ThreatLevel.CRITICAL), threat(ThreatLevel.CRITICAL))),
            ConnectionState.DISCONNECTED, true, true, true
        ).score.total
        assertTrue(dirty <= clean - 20)
        assertEquals(
            SecurityScoreEngine.Action.UNINSTALL_HIGH_RISK_APPS,
            engine.compute(scan(listOf(threat(ThreatLevel.HIGH))),
                ConnectionState.CONNECTED, true, true, true).actions.first()
        )
    }

    @Test
    fun `stale scan decays the threat pillar`() {
        val fresh = engine.compute(
            scan(emptyList()), ConnectionState.DISCONNECTED, true, true, true
        ).score.total
        val stale = engine.compute(
            scan(emptyList(), ageDays = 90), ConnectionState.DISCONNECTED, true, true, true
        ).score.total
        assertTrue(stale < fresh)
    }

    @Test
    fun `connecting state earns partial tunnel credit`() {
        val report = engine.compute(
            scan(emptyList()), ConnectionState.CONNECTING, true, true, true
        )
        assertEquals(18, report.score.pillarTunnel)
    }

    @Test
    fun `disabled optimizer features generate actionable advice`() {
        val report = engine.compute(
            scan(emptyList()), ConnectionState.CONNECTED,
            dnsCacheEnabled = false, autoMtuEnabled = false, autoReconnectEnabled = false
        )
        assertTrue(report.actions.contains(SecurityScoreEngine.Action.ENABLE_DNS_CACHE))
        assertTrue(report.actions.contains(SecurityScoreEngine.Action.ENABLE_AUTO_MTU))
        assertTrue(report.actions.contains(SecurityScoreEngine.Action.ENABLE_AUTO_RECONNECT))
    }

    @Test
    fun `score never leaves 0 to 100`() {
        val worst = engine.compute(
            scan(listOf(threat(ThreatLevel.CRITICAL))),
            ConnectionState.DISCONNECTED, false, false, false
        )
        assertTrue(worst.score.total in 0..100)
    }
}
