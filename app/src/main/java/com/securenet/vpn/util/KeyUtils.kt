package com.securenet.vpn.util

import java.util.Base64
import java.util.Locale

/**
 * WireGuard key handling utilities.
 *
 * Private keys are generated as 32 cryptographically-secure random bytes and
 * clamped exactly as RFC 7748 mandates; the public key derivation happens in
 * the wireguard-go layer (Curve25519 scalar multiplication).
 *
 * java.util.Base64 is used (not android.util.Base64) so the class is fully
 * exercisable in JVM unit tests; it is available on Android since API 26 and
 * this project's minSdk is 30.
 */
object KeyUtils {

    private val BASE64_KEY_REGEX = Regex("^[A-Za-z0-9+/]{42}[A-Za-z0-9+/=]==?$")

    /**
     * RFC 7748 §5 private-key clamping:
     *  - clear the 3 low bits of the first byte
     *  - clear the highest bit of the last byte
     *  - set the second-highest bit of the last byte
     */
    fun clampPrivateKey(key: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "WireGuard private key must be $KEY_SIZE bytes" }
        val out = key.copyOf()
        out[0] = (out[0].toInt() and 248).toByte()
        out[31] = ((out[31].toInt() and 127) or 64).toByte()
        return out
    }

    fun isValidBase64Key(value: String): Boolean {
        if (!BASE64_KEY_REGEX.matches(value.trim())) return false
        return try {
            Base64.getDecoder().decode(value).size == KEY_SIZE
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun base64ToHex(base64: String): String {
        val bytes = Base64.getDecoder().decode(base64.trim())
        require(bytes.size == KEY_SIZE) { "Key must decode to $KEY_SIZE bytes" }
        return bytes.joinToString(separator = "") { String.format(Locale.US, "%02x", it) }
    }

    /**
     * Builds the settings string consumed by wireguard-go's device.IpcSet
     * (the exact format wgTurnOn expects). Keys must be hex-encoded.
     */
    fun buildWgUserspaceSettings(
        privateKeyBase64: String,
        peerPublicKeyBase64: String,
        endpointHost: String,
        endpointPort: Int,
        allowedIps: List<String>,
        persistentKeepaliveSeconds: Int = 25,
        presharedKeyBase64: String? = null
    ): String {
        require(isValidBase64Key(privateKeyBase64)) { "Invalid private key" }
        require(isValidBase64Key(peerPublicKeyBase64)) { "Invalid peer public key" }
        require(allowedIps.isNotEmpty()) { "At least one allowed_ip is required" }
        if (presharedKeyBase64 != null) {
            require(isValidBase64Key(presharedKeyBase64)) { "Invalid preshared key" }
        }

        return buildString {
            append("private_key=").append(base64ToHex(privateKeyBase64)).append('\n')
            append("public_key=").append(base64ToHex(peerPublicKeyBase64)).append('\n')
            if (presharedKeyBase64 != null) {
                append("preshared_key=").append(base64ToHex(presharedKeyBase64)).append('\n')
            }
            append("endpoint=").append(endpointHost).append(':').append(endpointPort).append('\n')
            allowedIps.forEach { append("allowed_ip=").append(it).append('\n') }
            append("persistent_keepalive_interval=").append(persistentKeepaliveSeconds).append('\n')
        }
    }

    /** Bytes -> WireGuard-style base64 key. */
    fun toBase64Key(bytes: ByteArray): String {
        require(bytes.size == KEY_SIZE) { "Key must be $KEY_SIZE bytes" }
        return Base64.getEncoder().encodeToString(bytes)
    }

    const val KEY_SIZE = 32

    /** IPv4 wildcard route for full-tunnel configuration. */
    const val ROUTE_ALL_V4 = "0.0.0.0/0"

    /** IPv6 wildcard route for full-tunnel configuration. */
    const val ROUTE_ALL_V6 = "::/0"
}
