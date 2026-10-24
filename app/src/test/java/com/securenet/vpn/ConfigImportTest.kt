package com.securenet.vpn

import com.securenet.vpn.data.local.CustomServerStore
import com.securenet.vpn.util.ConfigImporter
import com.securenet.vpn.util.KeyUtils
import com.securenet.vpn.util.WgConfigBuilder
import com.securenet.vpn.util.WireGuardConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.5 — WireGuard profile import pipeline: parse → validate → store, plus
 * the QR export round trip.
 */
class ConfigImportTest {

    private val privateKey = KeyUtils.toBase64Key(ByteArray(32) { (it + 1).toByte() })
    private val peerKey = KeyUtils.toBase64Key(ByteArray(32) { (63 - it).toByte() })
    private val psk = KeyUtils.toBase64Key(ByteArray(32) { (it * 3).toByte() })

    private fun validConf(
        withPsk: Boolean = false,
        keepalive: Int? = 25
    ): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = $privateKey")
        appendLine("Address = 10.7.0.2/24, fd42::7/128")
        appendLine("DNS = 9.9.9.9, 149.112.112.112")
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = $peerKey")
        if (withPsk) appendLine("PresharedKey = $psk")
        appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
        appendLine("Endpoint = vpn.example.net:4711")
        keepalive?.let { appendLine("PersistentKeepalive = $it") }
    }

    // --------------------------------------------------------------
    // ConfigImporter
    // --------------------------------------------------------------

    @Test
    fun `valid conf imports with all fields mapped`() {
        val outcome = ConfigImporter.import(
            ConfigImporter.ImportSpec(validConf(), "My Tunnel", "file")
        )
        val entry = (outcome as ConfigImporter.Outcome.Success).entry
        assertEquals("My Tunnel", entry.server.name)
        assertEquals("vpn.example.net", entry.server.endpointHost)
        assertEquals(4711, entry.server.endpointPort)
        assertEquals(privateKey, entry.privateKey)
        assertNull(entry.presharedKey)
        assertEquals("10.7.0.2", entry.server.localAddressV4)
        assertEquals(24, entry.server.localPrefixV4)
        assertEquals("9.9.9.9", entry.server.dnsPrimary)
        assertEquals("149.112.112.112", entry.server.dnsSecondary)
        assertEquals(peerKey, entry.server.publicKey)
        assertTrue(entry.server.supportsIpv6)
        assertEquals("file", entry.source)
        assertTrue(entry.server.id.startsWith("custom_"))
    }

    @Test
    fun `preshared key is captured and validated`() {
        val ok = ConfigImporter.import(
            ConfigImporter.ImportSpec(validConf(withPsk = true), null, "paste")
        )
        assertEquals(psk, (ok as ConfigImporter.Outcome.Success).entry.presharedKey)

        val bad = ConfigImporter.import(
            ConfigImporter.ImportSpec(
                validConf(withPsk = true).replace("PresharedKey = $psk", "PresharedKey = short"),
                null, "paste"
            )
        )
        assertEquals(
            ConfigImporter.Reason.BAD_PRESHARED_KEY,
            (bad as ConfigImporter.Outcome.Error).reason
        )
    }

    @Test
    fun `unknown wg-quick keys like SaveConfig no longer reject real world files`() {
        // SaveConfig is an [Interface] option real-world exporters write.
        val conf = validConf().replace(
            "DNS = 9.9.9.9, 149.112.112.112\n",
            "DNS = 9.9.9.9, 149.112.112.112\nSaveConfig = true\n"
        )
        val outcome = ConfigImporter.import(
            ConfigImporter.ImportSpec(conf, null, "file")
        )
        assertTrue(outcome is ConfigImporter.Outcome.Success)
    }

    @Test
    fun `defaults fill in for missing optional fields`() {
        val minimal = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $privateKey")
            appendLine("[Peer]")
            appendLine("PublicKey = $peerKey")
            appendLine("Endpoint = 1.2.3.4:51820")
        }
        val entry = (
            ConfigImporter.import(ConfigImporter.ImportSpec(minimal, null, "paste"))
                as ConfigImporter.Outcome.Success
            ).entry
        assertEquals("10.66.66.2", entry.server.localAddressV4)
        assertEquals(32, entry.server.localPrefixV4)
        assertEquals("1.1.1.1", entry.server.dnsPrimary)
        assertFalse(entry.server.supportsIpv6) // no v6 allowed-ips/addresses
    }

    @Test
    fun `validation ladder rejects bad inputs`() {
        fun err(raw: String): ConfigImporter.Reason =
            (ConfigImporter.import(ConfigImporter.ImportSpec(raw, null, "qr"))
                as ConfigImporter.Outcome.Error).reason

        assertEquals(ConfigImporter.Reason.EMPTY, err("   "))
        assertEquals(
            ConfigImporter.Reason.BAD_PRIVATE_KEY,
            err(validConf().replace(privateKey, "not-base64!!"))
        )
        assertEquals(
            ConfigImporter.Reason.BAD_PEER_KEY,
            err(validConf().replace(peerKey, "short"))
        )
        assertEquals(
            ConfigImporter.Reason.MISSING_ENDPOINT,
            err(validConf().replace("Endpoint = vpn.example.net:4711\n", ""))
        )
        assertEquals(
            ConfigImporter.Reason.BAD_ENDPOINT,
            err(validConf().replace("vpn.example.net:4711", "vpn.example.net"))
        )
        assertEquals(
            ConfigImporter.Reason.PARSE_FAILED,
            err("hello world, no sections here")
        )
    }

    @Test
    fun `endpoint parser handles v4 host and bracketed v6 only`() {
        assertEquals("gw.example.com" to 51820,
            ConfigImporter.splitEndpoint("gw.example.com:51820"))
        assertEquals("2001:db8::1" to 51820,
            ConfigImporter.splitEndpoint("[2001:db8::1]:51820"))
        assertNull(ConfigImporter.splitEndpoint("2001:db8::1:51820")) // unbracketed v6
        assertNull(ConfigImporter.splitEndpoint("host:0"))
        assertNull(ConfigImporter.splitEndpoint("host:99999"))
        assertNull(ConfigImporter.splitEndpoint(":51820"))
        assertNull(ConfigImporter.splitEndpoint("host:"))
    }

    @Test
    fun `display name defaults to the endpoint host label`() {
        val entry = (
            ConfigImporter.import(ConfigImporter.ImportSpec(validConf(), null, "qr"))
                as ConfigImporter.Outcome.Success
            ).entry
        assertEquals("vpn", entry.server.name)
    }

    // --------------------------------------------------------------
    // CustomServerStore
    // --------------------------------------------------------------

    private fun sampleEntry(id: String = "custom_abc12345") =
        CustomServerStore.Entry(
            server = com.securenet.vpn.data.model.VpnServer(
                id = id, name = "Lab", countryCode = "WG", city = "Lab",
                endpointHost = "10.0.0.1", endpointPort = 51820,
                publicKey = peerKey
            ),
            privateKey = privateKey,
            presharedKey = psk,
            source = "qr"
        )

    @Test
    fun `store round trip preserves all fields`() {
        val json = CustomServerStore.upsert("[]", sampleEntry())
        val loaded = CustomServerStore.load(json)
        assertEquals(1, loaded.size)
        assertEquals(sampleEntry(), loaded.first())
    }

    @Test
    fun `upsert replaces by id and puts newest first`() {
        var json = CustomServerStore.upsert("[]", sampleEntry("custom_a"))
        json = CustomServerStore.upsert(json, sampleEntry("custom_b"))
        assertEquals(listOf("custom_b", "custom_a"),
            CustomServerStore.load(json).map { it.server.id })
        json = CustomServerStore.upsert(json, sampleEntry("custom_a"))
        assertEquals(listOf("custom_a", "custom_b"),
            CustomServerStore.load(json).map { it.server.id })
        assertEquals(2, CustomServerStore.count(json))
    }

    @Test
    fun `remove drops only the target profile`() {
        var json = CustomServerStore.upsert("[]", sampleEntry("custom_a"))
        json = CustomServerStore.upsert(json, sampleEntry("custom_b"))
        json = CustomServerStore.remove(json, "custom_a")
        assertEquals(listOf("custom_b"), CustomServerStore.load(json).map { it.server.id })
        assertEquals(0, CustomServerStore.count(CustomServerStore.remove(json, "custom_b")))
    }

    @Test
    fun `corrupt json degrades to empty registry`() {
        assertEquals(0, CustomServerStore.load("not json at all").size)
        assertEquals(0, CustomServerStore.load("").size)
        assertEquals(0, CustomServerStore.load("""[{"server":"{""").size)
        // Garbage entries are skipped, healthy ones survive.
        val json = CustomServerStore.upsert("[]", sampleEntry())
        val mixed = """[{"nonsense":1},"string-entry",${json.substring(1, json.length - 1)}]"""
        val loaded = CustomServerStore.load(mixed)
        assertEquals(1, loaded.size)
        assertEquals("custom_abc12345", loaded.first().server.id)
    }

    @Test
    fun `find resolves per-server keys`() {
        val json = CustomServerStore.upsert("[]", sampleEntry())
        assertNotNull(CustomServerStore.find(json, "custom_abc12345")?.privateKey)
        assertNull(CustomServerStore.find(json, "srv_stock")?.privateKey)
    }

    // --------------------------------------------------------------
    // WgConfigBuilder (QR export payload)
    // --------------------------------------------------------------

    @Test
    fun `built conf round trips through the parser`() {
        val server = sampleEntry().server.copy(
            supportsIpv6 = true,
            localAddressV4 = "10.7.0.2",
            localPrefixV4 = 24,
            dnsPrimary = "9.9.9.9",
            dnsSecondary = "149.112.112.112"
        )
        val conf = WgConfigBuilder.buildConf(server, privateKey, psk)
        val parsed = WireGuardConfigParser.parse(conf)
        val cfg = (parsed as WireGuardConfigParser.ParseResult.Success).config

        assertEquals(privateKey, cfg.interfacePrivateKey)
        assertEquals(listOf("10.7.0.2/24"), cfg.interfaceAddresses)
        assertEquals(peerKey, cfg.peerPublicKey)
        assertEquals(psk, cfg.peerPresharedKey)
        assertEquals("10.0.0.1:51820", cfg.peerEndpoint)
        assertTrue(cfg.peerAllowedIps.contains("0.0.0.0/0"))
        assertTrue(cfg.peerAllowedIps.contains("::/0"))
        assertEquals(25, cfg.persistentKeepalive)
    }

    @Test
    fun `re-importing an exported conf reproduces the same server`() {
        val original = sampleEntry()
        val conf = WgConfigBuilder.buildConf(original.server, privateKey, original.presharedKey)
        val re = ConfigImporter.import(
            ConfigImporter.ImportSpec(conf, "Re-imported", "qr")
        )
        val entry = (re as ConfigImporter.Outcome.Success).entry
        assertEquals(original.server.endpointHost, entry.server.endpointHost)
        assertEquals(original.server.endpointPort, entry.server.endpointPort)
        assertEquals(original.server.publicKey, entry.server.publicKey)
        assertEquals(original.privateKey, entry.privateKey)
        assertEquals(original.presharedKey, entry.presharedKey)
        // A fresh id must be issued so re-import does not collide.
        assertNotEquals(original.server.id, entry.server.id)
    }
}
