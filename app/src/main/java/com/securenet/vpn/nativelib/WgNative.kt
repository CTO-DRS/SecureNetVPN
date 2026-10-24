package com.securenet.vpn.nativelib

/**
 * Thin, honest boundary to the wireguard-go userspace backend.
 *
 * The symbols below are exported by libwgcore.so, which is produced from
 * go/libwg (a c-shared build wrapping golang.zx2c4.com/wireguard). The JNI
 * glue that maps these Java methods onto the C ABI lives in
 * cpp/wg_bridge.c. When the .so is absent (clean checkout without running
 * go/build-wireguard-go.sh), [tryLoad] returns false and the service runs
 * in clearly-logged simulation mode so the UI remains demonstrable.
 */
object WgNative {

    @Volatile
    private var loaded = false

    @Volatile
    private var attempted = false

    /** True when libwgcore.so is resident and the WireGuard API is live. */
    val isAvailable: Boolean
        get() {
            if (!attempted) tryLoad()
            return loaded
        }

    @Synchronized
    fun tryLoad(): Boolean {
        if (attempted) return loaded
        attempted = true
        loaded = try {
            System.loadLibrary("wgcore")
            true
        } catch (t: UnsatisfiedLinkError) {
            android.util.Log.w(TAG, "libwgcore.so not present; WireGuard Go backend unavailable", t)
            false
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Failed to load WireGuard Go backend", t)
            false
        }
        return loaded
    }

    /**
     * Brings a tunnel up from an already-established TUN file descriptor.
     * @param ifName interface label, informational only
     * @param tunFd the fd from VpnService.Builder.establish()
     * @param settings device.IpcSet format (hex keys, see KeyUtils)
     * @return opaque handle (>0) or a negative error code
     */
    external fun wgTurnOn(ifName: String, tunFd: Int, settings: String): Int

    /** Shuts the tunnel down and frees the handle. */
    external fun wgTurnOff(handle: Int)

    /** 0 = down, 1 = up, -1 = unknown/invalid handle. */
    external fun wgGetState(handle: Int): Int

    /** Current device.IpcGet dump; empty string when unavailable. */
    external fun wgGetConfig(handle: Int): String

    /** wireguard-go version string, e.g. "0.0.20230223". */
    external fun wgVersion(): String

    private const val TAG = "WgNative"
}
