package com.securenet.vpn

import com.securenet.vpn.util.KeyUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyUtilsTest {

    private fun keyBytes(vararg firstByteValues: Int) = ByteArray(32) { i -> i.toByte() }

    @Test
    fun `clamping follows RFC 7748`() {
        val key = ByteArray(32) { i -> 0xFF.toByte() }
        val clamped = KeyUtils.clampPrivateKey(key)

        // First byte: 0xFF & 248 = 0xF8
        assertEquals(0xF8.toByte(), clamped[0])
        // Last byte: (0xFF & 127) | 64 = 0x40 | 0x40 = 0x40? -> 0xFF & 127 = 0x7F, |64 -> 0x7F
        assertEquals(0x7F.toByte(), clamped[31])

        // Original array must not be mutated.
        assertEquals(0xFF.toByte(), key[0])
        assertEquals(0xFF.toByte(), key[31])
    }

    @Test
    fun `clamping preserves RFC test vectors low bits`() {
        val key = keyBytes()
        key[0] = 0x07
        key[31] = 0x20.toByte()
        val clamped = KeyUtils.clampPrivateKey(key)
        assertEquals(0x00, clamped[0].toInt() and 0x07)
        assertEquals(0x40, clamped[31].toInt() and 0x40)
        assertEquals(0x00, clamped[31].toInt() and 0x80)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `clamping rejects wrong size`() {
        KeyUtils.clampPrivateKey(ByteArray(31))
    }

    @Test
    fun `base64 round trip preserves bytes`() {
        val key = keyBytes()
        val b64 = KeyUtils.toBase64Key(key)
        assertTrue(KeyUtils.isValidBase64Key(b64))
        assertArrayEquals(
            key,
            java.util.Base64.getDecoder().decode(b64)
        )
    }

    @Test
    fun `invalid base64 keys are rejected`() {
        assertFalse(KeyUtils.isValidBase64Key(""))
        assertFalse(KeyUtils.isValidBase64Key("too-short"))
        assertFalse(KeyUtils.isValidBase64Key("!!!!not-base64!!!!"))
        // Correct shape but wrong decoded length (44 chars of valid b64 -> 33 bytes)
        assertFalse(KeyUtils.isValidBase64Key("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vPz9A"))
    }

    @Test
    fun `base64 to hex conversion is correct`() {
        val key = ByteArray(4) { 0 } + ByteArray(28) { 0 }
        val b64 = KeyUtils.toBase64Key(key)
        val hex = KeyUtils.base64ToHex(b64)
        assertEquals(64, hex.length)
        assertTrue(hex.all { it == '0' })
    }

    @Test
    fun `userspace settings use wireguard-go IPC format`() {
        // 32-byte key of 0x01 -> 64 hex chars of 01.
        val key = ByteArray(32) { 0x01 }
        val keyB64 = KeyUtils.toBase64Key(key)
        val settings = KeyUtils.buildWgUserspaceSettings(
            privateKeyBase64 = keyB64,
            peerPublicKeyBase64 = keyB64,
            endpointHost = "vpn.example.com",
            endpointPort = 51820,
            allowedIps = listOf(KeyUtils.ROUTE_ALL_V4, KeyUtils.ROUTE_ALL_V6),
            persistentKeepaliveSeconds = 25
        )

        val lines = settings.trim().split('\n')
        assertTrue(lines.contains("private_key=" + "01".repeat(32)))
        assertTrue(lines.contains("public_key=" + "01".repeat(32)))
        assertTrue(lines.contains("endpoint=vpn.example.com:51820"))
        assertTrue(lines.contains("allowed_ip=0.0.0.0/0"))
        assertTrue(lines.contains("allowed_ip=::/0"))
        assertTrue(lines.contains("persistent_keepalive_interval=25"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `userspace settings reject invalid private key`() {
        KeyUtils.buildWgUserspaceSettings(
            privateKeyBase64 = "bad-key",
            peerPublicKeyBase64 = KeyUtils.toBase64Key(keyBytes()),
            endpointHost = "h",
            endpointPort = 1,
            allowedIps = listOf("0.0.0.0/0")
        )
    }
}
