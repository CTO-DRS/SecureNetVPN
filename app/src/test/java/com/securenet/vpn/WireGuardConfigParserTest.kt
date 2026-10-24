package com.securenet.vpn

import com.securenet.vpn.util.WireGuardConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardConfigParserTest {

    @Test
    fun `parses a complete wg-quick configuration`() {
        val raw = """
            # Personal tunnel
            [Interface]
            PrivateKey = ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/==
            Address = 10.66.66.2/32
            DNS = 1.1.1.1, 8.8.8.8

            [Peer]
            PublicKey = ZYXWVUTSRQPONMLKJIHGFEDCBAzyxwvutsrqponmlkjihgfedcba0987654321+/==
            Endpoint = vpn.securenet.dev:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
        """.trimIndent()

        val result = WireGuardConfigParser.parse(raw)
        assertTrue(result is WireGuardConfigParser.ParseResult.Success)
        val config = (result as WireGuardConfigParser.ParseResult.Success).config

        assertEquals(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/==",
            config.interfacePrivateKey
        )
        assertEquals(listOf("10.66.66.2/32"), config.interfaceAddresses)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), config.interfaceDnsServers)
        assertEquals(
            "ZYXWVUTSRQPONMLKJIHGFEDCBAzyxwvutsrqponmlkjihgfedcba0987654321+/==",
            config.peerPublicKey
        )
        assertEquals("vpn.securenet.dev:51820", config.peerEndpoint)
        assertEquals(listOf("0.0.0.0/0", "::/0"), config.peerAllowedIps)
        assertEquals(25, config.persistentKeepalive)
    }

    @Test
    fun `rejects missing private key`() {
        val raw = """
            [Interface]
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = key
        """.trimIndent()
        val result = WireGuardConfigParser.parse(raw)
        assertTrue(result is WireGuardConfigParser.ParseResult.Failure)
        assertTrue(
            (result as WireGuardConfigParser.ParseResult.Failure).reason.contains("PrivateKey")
        )
    }

    @Test
    fun `rejects unknown interface keys`() {
        val raw = """
            [Interface]
            PrivateKey = key
            BogusOption = 1
        """.trimIndent()
        val result = WireGuardConfigParser.parse(raw)
        assertTrue(result is WireGuardConfigParser.ParseResult.Failure)
    }

    @Test
    fun `rejects malformed lines`() {
        val result = WireGuardConfigParser.parse("no-equals-sign-here")
        assertTrue(result is WireGuardConfigParser.ParseResult.Failure)
    }

    @Test
    fun `empty input fails`() {
        val result = WireGuardConfigParser.parse("   \n  ")
        assertTrue(result is WireGuardConfigParser.ParseResult.Failure)
        assertTrue((result as WireGuardConfigParser.ParseResult.Failure).reason.contains("Empty"))
    }

    @Test
    fun `comments and blank lines are ignored`() {
        val raw = """
            # leading comment

            [Interface]
            PrivateKey = k

            # another comment

            [Peer]
            PublicKey = p
        """.trimIndent()
        val result = WireGuardConfigParser.parse(raw)
        assertTrue(result is WireGuardConfigParser.ParseResult.Success)
        val config = (result as WireGuardConfigParser.ParseResult.Success).config
        assertEquals("k", config.interfacePrivateKey)
        assertEquals("p", config.peerPublicKey)
        assertNull(config.peerEndpoint)
    }
}
