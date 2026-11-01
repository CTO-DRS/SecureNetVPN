package com.securenet.vpn.nativelib

/**
 * Thin, honest boundary to the wireguard-go userspace backend.
 *
 * The symbols below are exported by libwgcore.so, which is produced from
 * go/libwg (a c-shared build wrapping golang.zx2c4.com/wireguard). The JNI
 * glue that maps these Java methods onto the C ABI lives in
 * cpp/securenet-jni.c, compiled into libsecurenet.so.
 *
 * v1.8.25 — loading order is part of correctness: the `Java_..._WgNative_*`
 * symbols exist ONLY in libsecurenet.so, and ART resolves `external fun`
 * calls against the libraries registered in the process. A cold service
 * start (boot auto-connect, quick widget, tile, schedule fire) may never
 * have run an activity that touched NativeBridge — so [tryLoad] brings the
 * glue in FIRST and reports availability only when both libraries are
 * resident. When either is absent the service runs in clearly-logged
 * simulation mode so the UI remains demonstrable.
 */
object WgNative {

    @Volatile
    private var loaded = false

    @Volatile
    private var attempted = false

    /** v1.8.25 — true when libsecurenet.so (the JNI glue) is resident. */
    @Volatile
    var glueLoaded = false
        private set

    /** v1.8.25 — true when libwgcore.so (the wireguard-go data plane) is resident. */
    @Volatile
    var coreLoaded = false
        private set

    /** True when the JNI glue AND the WireGuard core are both resident. */
    val isAvailable: Boolean
        get() {
            if (!attempted) tryLoad()
            return loaded
        }

    @Synchronized
    fun tryLoad(): Boolean {
        if (attempted) return loaded
        attempted = true
        // v1.8.25 — glue first (idempotent with NativeBridge's own load), so
        // the Java_..._WgNative_* symbols are registered before any call.
        glueLoaded = try {
            System.loadLibrary("securenet")
            true
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "libsecurenet.so (JNI glue) unavailable", t)
            false
        }
        coreLoaded = try {
            System.loadLibrary("wgcore")
            true
        } catch (t: UnsatisfiedLinkError) {
            android.util.Log.w(TAG, "libwgcore.so not present; WireGuard Go backend unavailable", t)
            false
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Failed to load WireGuard Go backend", t)
            false
        }
        loaded = glueLoaded && coreLoaded
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
