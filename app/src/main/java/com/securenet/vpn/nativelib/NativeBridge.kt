package com.securenet.vpn.nativelib

/**
 * Bridge to our own hardened C++ core (libsecurenet.so): RFC 8439 ChaCha20,
 * FIPS 180-4 SHA-256 and the RFC 7748 key-clamping helper. The native side
 * carries a self-test (RFC 8439 + known SHA-256 vectors) surfaced via
 * [selfTest], used at startup and in instrumented tests.
 */
object NativeBridge {

    @Volatile
    private var loadState: Int = 0 // 0 = unattempted, 1 = ok, -1 = failed

    val isAvailable: Boolean
        get() {
            ensureLoaded()
            return loadState == 1
        }

    @Synchronized
    private fun ensureLoaded() {
        if (loadState != 0) return
        loadState = try {
            System.loadLibrary("securenet")
            1
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "libsecurenet.so failed to load", t)
            -1
        }
    }

    /** Pure C implementation of ChaCha20 (RFC 8439) keystream XOR. */
    external fun chacha20Xor(
        key: ByteArray,
        nonce: ByteArray,
        initialCounter: Int,
        data: ByteArray
    ): ByteArray

    /** SHA-256 digest of the input. */
    external fun sha256(data: ByteArray): ByteArray

    /** RFC 7748 clamp of a 32-byte private key. */
    external fun clampPrivateKey(key: ByteArray): ByteArray

    /** Runs RFC 8439 + SHA-256 known-answer tests inside the native lib. */
    external fun selfTest(): Boolean

    /** Build fingerprint of the C++ core for About/diagnostics. */
    external fun nativeVersion(): String

    private const val TAG = "NativeBridge"
}
