package com.securenet.vpn

import com.securenet.vpn.intelligence.NetworkAnomalyDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAnomalyDetectorTest {

    @Test
    fun `stable low-latency stream produces no anomalies`() {
        val detector = NetworkAnomalyDetector()
        val watchdog = detector.LatencyWatchdog()
        repeat(30) { assertNull(watchdog.observe(50.0)) }
        assertEquals(50.0, watchdog.baselineMs(), 5.0)
    }

    @Test
    fun `spike far above baseline is flagged`() {
        val detector = NetworkAnomalyDetector()
        val watchdog = detector.LatencyWatchdog()
        repeat(20) { watchdog.observe(40.0) }
        val anomaly = watchdog.observe(400.0)
        assertNotNull(anomaly)
        assertTrue(anomaly!!.zScore >= 3.0)
        assertTrue(anomaly.baselineMs < 100.0)
    }

    @Test
    fun `warmup samples are never flagged`() {
        val detector = NetworkAnomalyDetector()
        val watchdog = detector.LatencyWatchdog()
        assertNull(watchdog.observe(10.0))
        assertNull(watchdog.observe(10.0))
        assertNull(watchdog.observe(2000.0))   // within warmup window
    }

    @Test
    fun `negative and NaN samples are ignored`() {
        val detector = NetworkAnomalyDetector()
        val watchdog = detector.LatencyWatchdog()
        assertNull(watchdog.observe(-5.0))
        assertNull(watchdog.observe(Double.NaN))
        assertEquals(0, watchdog.sampleCount())
    }

    @Test
    fun `dns hijack outside trusted prefix is SUSPICIOUS`() {
        val detector = NetworkAnomalyDetector()
        val verdict = detector.checkDns("dns.google", "185.228.168.10")
        assertEquals(NetworkAnomalyDetector.DnsVerdict.SUSPICIOUS, verdict.verdict)
    }

    @Test
    fun `legitimate cloudflare address is TRUSTED`() {
        val detector = NetworkAnomalyDetector()
        val verdict = detector.checkDns("cloudflare-dns.com", "1.1.1.1")
        assertEquals(NetworkAnomalyDetector.DnsVerdict.TRUSTED, verdict.verdict)
    }

    @Test
    fun `unknown domain gets no opinion`() {
        val detector = NetworkAnomalyDetector()
        val verdict = detector.checkDns("example.org", "93.184.216.34")
        assertEquals(NetworkAnomalyDetector.DnsVerdict.UNKNOWN_DOMAIN, verdict.verdict)
    }

    @Test
    fun `trailing dot is normalized`() {
        val detector = NetworkAnomalyDetector()
        val verdict = detector.checkDns("one.one.one.one.", "1.0.0.1")
        assertEquals(NetworkAnomalyDetector.DnsVerdict.TRUSTED, verdict.verdict)
    }
}
