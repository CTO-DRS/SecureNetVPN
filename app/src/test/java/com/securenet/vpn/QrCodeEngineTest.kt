package com.securenet.vpn

import com.securenet.vpn.qr.QrCodeEngine
import com.securenet.vpn.util.KeyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.5 — QR payload encoding for WireGuard profile exchange (ZXing core,
 * pure JVM).
 */
class QrCodeEngineTest {

    @Test
    fun `small payload encodes to a square matrix`() {
        val payload = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${KeyUtils.toBase64Key(ByteArray(32) { 7 })}")
            appendLine("Address = 10.66.66.2/32")
            appendLine("[Peer]")
            appendLine("PublicKey = ${KeyUtils.toBase64Key(ByteArray(32) { 9 })}")
            appendLine("Endpoint = vpn.example.net:51820")
        }
        val result = QrCodeEngine.encode(payload)
        val matrix = (result as QrCodeEngine.EncodeResult.Success).matrix
        assertEquals(matrix.width, matrix.height)
        assertTrue(matrix.width > 0)
        // Quiet-zone margins mean corners stay white.
        assertTrue(!matrix[0, 0])
        assertTrue(!matrix[matrix.width - 1, matrix.height - 1])
        // And something is actually encoded in the middle.
        val darkModules = (0 until matrix.width).count { x -> (0 until matrix.height).any { y -> matrix[x, y] } }
        assertTrue(darkModules > matrix.width / 2)
    }

    @Test
    fun `oversized payload is rejected with a clear outcome`() {
        val huge = buildString { repeat(2_300) { append("a") } }
        val result = QrCodeEngine.encode(huge)
        assertTrue(result is QrCodeEngine.EncodeResult.TooLarge)
        assertEquals(2_300, (result as QrCodeEngine.EncodeResult.TooLarge).chars)
    }

    @Test
    fun `capacity limit stays within the QR spec at ECC-M`() {
        // 2 200 chars must still encode (a wireguard profile is ~300-700).
        val biggest = buildString { repeat(QrCodeEngine.MAX_PAYLOAD_CHARS) { append("x") } }
        assertTrue(QrCodeEngine.encode(biggest) is QrCodeEngine.EncodeResult.Success)
    }
}
