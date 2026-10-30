package com.securenet.vpn

import com.securenet.vpn.util.KeyUtils
import com.securenet.vpn.util.WireGuardConfigChecker
import com.securenet.vpn.util.WireGuardConfigChecker.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.16 — the pure offline WireGuard config checker behind
 * 🛡️ فاحص إعدادات WireGuard. The sheet paints whatever this returns, so
 * the rules matter: a valid config must come back clean (no false
 * alarms), every real defect must surface with the right code, severity
 * and line number, and full-tunnel AllowedIPs stays informational —
 * a legitimate choice, not an error.
 */
class WireGuardConfigCheckerTest {

    private val keyA = KeyUtils.toBase64Key(ByteArray(32) { 1 })
    private val keyB = KeyUtils.toBase64Key(ByteArray(32) { 2 })
    private val keyC = KeyUtils.toBase64Key(ByteArray(32) { 3 })

    private fun check(vararg lines: String): WireGuardConfigChecker.Report =
        WireGuardConfigChecker.check(lines.joinToString("\n"))

    private fun fullConfig() = listOf(
        "[Interface]",
        "PrivateKey = $keyA",
        "Address = 10.0.0.2/24",
        "DNS = 1.1.1.1, 8.8.8.8",
        "MTU = 1420",
        "ListenPort = 51820",
        "",
        "# a comment line is skipped",
        "[Peer]",
        "PublicKey = $keyB",
        "PresharedKey = $keyC",
        "Endpoint = vpn.example.com:51820",
        "AllowedIPs = 10.0.0.0/24",
        "PersistentKeepalive = 25"
    )

    private fun peerBlock(variant: (MutableList<String>.() -> Unit)? = null): List<String> {
        val lines = mutableListOf(
            "[Peer]",
            "PublicKey = $keyB",
            "Endpoint = peer.example.com:51820",
            "AllowedIPs = 10.64.0.0/10"
        )
        variant?.invoke(lines)
        return lines
    }

    @Test
    fun `valid config reports nothing at all`() {
        val report = check(*fullConfig().toTypedArray())
        assertEquals(0, report.errorCount)
        assertEquals(0, report.warningCount)
        assertEquals(1, report.interfaceCount)
        assertEquals(1, report.peerCount)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `blank input is an error, not a crash`() {
        val report = WireGuardConfigChecker.check("   \n  ")
        assertTrue(report.hasErrors)
        assertEquals("empty_config", report.issues.first().code)
        assertEquals(0, report.interfaceCount)
        assertEquals(0, report.peerCount)
    }

    @Test
    fun `missing interface section is an error`() {
        val report = check(
            "[Peer]",
            "PublicKey = $keyB",
            "Endpoint = peer.example.com:51820",
            "AllowedIPs = 0.0.0.0/0"
        )
        assertEquals(
            listOf("missing_interface"),
            report.issues.filter { it.severity == Severity.ERROR }.map { it.code }
        )
    }

    @Test
    fun `interface without private key`() {
        val report = check(
            "[Interface]",
            "Address = 10.0.0.2/24",
            *peerBlock().toTypedArray()
        )
        assertEquals(
            listOf("missing_private_key"),
            report.issues.filter { it.severity == Severity.ERROR }.map { it.code }
        )
    }

    @Test
    fun `interface without address`() {
        val report = check(
            "[Interface]",
            "PrivateKey = $keyA",
            *peerBlock().toTypedArray()
        )
        assertEquals(
            listOf("missing_address"),
            report.issues.filter { it.severity == Severity.ERROR }.map { it.code }
        )
    }

    @Test
    fun `invalid private key is reported once, not also as missing`() {
        val report = check(
            "[Interface]",
            "PrivateKey = not-a-real-key",
            "Address = 10.0.0.2/24",
            *peerBlock().toTypedArray()
        )
        val keyIssues = report.issues.filter { it.code == "invalid_key" }
        assertEquals(1, keyIssues.size)
        assertEquals(Severity.ERROR, keyIssues.first().severity)
        assertEquals("PrivateKey", keyIssues.first().detail)
        assertTrue(report.issues.none { it.code == "missing_private_key" })
    }

    @Test
    fun `peer without public key`() {
        val report = check(
            "[Interface]",
            "PrivateKey = $keyA",
            "Address = 10.0.0.2/24",
            "[Peer]",
            "Endpoint = peer.example.com:51820",
            "AllowedIPs = 10.64.0.0/10"
        )
        assertEquals(
            listOf("missing_public_key"),
            report.issues.filter { it.severity == Severity.ERROR }.map { it.code }
        )
    }

    @Test
    fun `peer missing endpoint and allowedips warns twice`() {
        val report = check(
            "[Interface]",
            "PrivateKey = $keyA",
            "Address = 10.0.0.2/24",
            "[Peer]",
            "PublicKey = $keyB"
        )
        assertTrue(report.issues.none { it.severity == Severity.ERROR })
        assertEquals(
            setOf("missing_allowed_ips", "missing_endpoint"),
            report.issues.filter { it.severity == Severity.WARNING }.map { it.code }.toSet()
        )
    }

    @Test
    fun `broken allowedips values are caught individually`() {
        val report = check(
            "[Interface]",
            "PrivateKey = $keyA",
            "Address = 10.0.0.2/24",
            "[Peer]",
            "PublicKey = $keyB",
            "Endpoint = peer.example.com:51820",
            "AllowedIPs = 10.0.0.0/99, 999.1.1.1, 192.168.1.0/24"
        )
        assertEquals(2, report.issues.count { it.code == "invalid_cidr" })
        assertEquals("10.0.0.0/99", report.issues.first { it.code == "invalid_cidr" }.detail)
        // the good CIDR stays silent
        assertTrue(report.issues.none { it.detail == "192.168.1.0/24" })
    }

    @Test
    fun `invalid endpoint forms are rejected`() {
        val base = listOf(
            "[Interface]",
            "PrivateKey = $keyA",
            "Address = 10.0.0.2/24",
            "[Peer]",
            "PublicKey = $keyB",
            "AllowedIPs = 10.64.0.0/10"
        )
        listOf("Endpoint = host-only", "Endpoint = host:0", "Endpoint = :51820")
            .forEach { bad ->
                val report = check(*base.toTypedArray(), bad)
                assertTrue(
                    "expected invalid_endpoint for: $bad",
                    report.issues.any {
                        it.code == "invalid_endpoint" && it.severity == Severity.ERROR
                    }
                )
            }
    }

    @Test
    fun `mtu rules — range is an error, low range is a warning`() {
        val iface = listOf(
            "[Interface]",
            "PrivateKey = $keyA",
            "Address = 10.0.0.2/24"
        )
        val peer = peerBlock()
        val tooLow = check(*iface.toTypedArray(), "MTU = 100", *peer.toTypedArray())
        assertEquals(
            "invalid_mtu",
            tooLow.issues.first { it.severity == Severity.ERROR }.code
        )
        val notANumber = check(*iface.toTypedArray(), "MTU = abc", *peer.toTypedArray())
        assertTrue(notANumber.issues.any { it.code == "invalid_mtu" })
        val low = check(*iface.toTypedArray(), "MTU = 1000", *peer.toTypedArray())
        assertEquals("low_mtu", low.issues.first { it.severity == Severity.WARNING }.code)
        assertEquals(0, low.errorCount)
    }

    @Test
    fun `unknown key warns with the raw key name`() {
        val report = check(
            "[Interface]",
            "PrivateKey = $keyA",
            "Address = 10.0.0.2/24",
            "PriavteKey2 = oops-typo",
            *peerBlock().toTypedArray()
        )
        val unknown = report.issues.first { it.code == "unknown_key" }
        assertEquals(Severity.WARNING, unknown.severity)
        assertEquals("PriavteKey2", unknown.detail)
        assertEquals(0, report.errorCount)
    }

    @Test
    fun `interface-only config warns about the missing peer`() {
        val report = check(
            "[Interface]",
            "PrivateKey = $keyA",
            "Address = 10.0.0.2/24"
        )
        assertEquals(0, report.errorCount)
        assertEquals(1, report.warningCount)
        assertEquals("no_peer", report.issues.first().code)
    }

    @Test
    fun `full tunnel allowedips stays informational`() {
        val report = check(
            "[Interface]",
            "PrivateKey = $keyA",
            "Address = 10.0.0.2/24",
            "[Peer]",
            "PublicKey = $keyB",
            "Endpoint = peer.example.com:51820",
            "AllowedIPs = 0.0.0.0/0, ::/0"
        )
        assertEquals(0, report.errorCount)
        assertEquals(0, report.warningCount)
        val infos = report.issues.filter { it.severity == Severity.INFO }
        assertEquals(listOf("0.0.0.0/0", "::/0"), infos.map { it.detail })
    }

    @Test
    fun `duplicate key warns and keeps the first line`() {
        val report = check(
            "[Interface]",
            "PrivateKey = $keyA",
            "Address = 10.0.0.2/24",
            "DNS = 1.1.1.1",
            "DNS = 1.1.1.1",
            *peerBlock().toTypedArray()
        )
        val dup = report.issues.first { it.code == "duplicate_key" }
        assertEquals(Severity.WARNING, dup.severity)
        assertEquals("DNS", dup.detail)
    }

    @Test
    fun `line numbers anchor the report`() {
        val report = check(
            "[Interface]",                 // 1
            "PrivateKey = $keyA",          // 2
            "Address = 10.0.0.2/24",       // 3
            "MTU = 12",                    // 4
            "[Peer]",                      // 5
            "PublicKey = $keyB",           // 6
            "Endpoint = peer.example.com:51820", // 7
            "AllowedIPs = 10.0.0.0/24"     // 8
        )
        val mtu = report.issues.first { it.code == "invalid_mtu" }
        assertEquals(4, mtu.line)
    }

    @Test
    fun `ipv6 addresses, endpoints and prefix bounds parse`() {
        val report = check(
            "[Interface]",
            "PrivateKey = $keyA",
            "Address = fd00::2/64, 10.0.0.2/24",
            "DNS = 2606:4700:4700::1111",
            "[Peer]",
            "PublicKey = $keyB",
            "Endpoint = [2001:db8::1]:51820",
            "AllowedIPs = 2001:db8::/32"
        )
        assertTrue(report.issues.isEmpty())
        assertEquals(0, report.errorCount)
        assertEquals(0, report.warningCount)
    }

    @Test
    fun `unparsable and stray lines warn`() {
        val stray = WireGuardConfigChecker.check("just some garbage line")
        assertTrue(stray.issues.any { it.code == "stray_line" })
        val unparsed = check(
            "[Interface]",
            "no-equals-sign-here",
            "PrivateKey = $keyA",
            "Address = 10.0.0.2/24",
            *peerBlock().toTypedArray()
        )
        assertTrue(unparsed.issues.any { it.code == "unparsed_line" })
    }

    @Test
    fun `validator helpers follow the documented rules`() {
        assertTrue(WireGuardConfigChecker.isIpv4("192.168.1.1"))
        assertFalse(WireGuardConfigChecker.isIpv4("999.1.1.1"))
        assertFalse(WireGuardConfigChecker.isIpv4("1.2.3"))
        assertTrue(WireGuardConfigChecker.isIpv6("fe80::1"))
        assertTrue(WireGuardConfigChecker.isIpv6("::"))
        assertFalse(WireGuardConfigChecker.isIpv6("1::2::3"))
        assertFalse(WireGuardConfigChecker.isIpv6("1.2.3.4"))
        assertFalse(WireGuardConfigChecker.isIpOrCidr("fd00::2/129"))
        assertTrue(WireGuardConfigChecker.isIpOrCidr("fd00::2/128"))
        assertTrue(WireGuardConfigChecker.isHostname("vpn.example.com"))
        assertFalse(WireGuardConfigChecker.isHostname("-bad-"))
        assertFalse(WireGuardConfigChecker.isEndpoint("host:70000"))
        assertTrue(WireGuardConfigChecker.isEndpoint("host:65535"))
    }
}
