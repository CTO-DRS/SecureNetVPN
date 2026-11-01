package com.securenet.vpn.nativelib

/**
 * v1.8.25 — the honest engine card, decided purely.
 *
 * SecureNet's WireGuard data plane is its own: wireguard-go compiled as a
 * c-shared libwgcore.so (MIT), bridged through libsecurenet.so, driven by
 * [WgNative] inside the app's own VpnService — no external app, no
 * subscription, no closed component. This object turns the raw loader
 * facts (which libraries are resident, which versions reported, whether
 * the crypto self-test passed) into the one decision the user cares
 * about: is the tunnel engine REAL or is this build in simulation mode —
 * and if it is simulated, exactly which piece is missing.
 *
 * Pure data + pure decisions: no android imports, fully JVM-testable
 * ([EngineStatusTest] pins the truth table, the reason ladder and the
 * snapshot coherence rules). The Settings screen renders a [Snapshot]
 * through localized strings; this layer never formats user text.
 */
object EngineStatus {

    /** REAL = both native libraries resident; SIMULATION = demonstrable only. */
    enum class Mode { REAL, SIMULATION }

    /**
     * Why a build is in simulation mode, in audit order: the core is the
     * data plane, the glue is the bridge; without either, honest reporting
     * beats a vague failure later.
     */
    enum class Reason { NONE, CORE_MISSING, GLUE_MISSING }

    /**
     * One immutable engine reading.
     *
     * @param glueLoaded  libsecurenet.so resident (JNI symbols registered)
     * @param coreLoaded  libwgcore.so resident (wireguard-go data plane)
     * @param wgVersion   wireguard-go version string, null when unavailable
     * @param cppVersion  C++ crypto core version string, null when unavailable
     * @param selfTestPassed  RFC 8439 / SHA-256 known-answer result;
     *                        null = not run yet (cold reading)
     */
    data class Snapshot(
        val glueLoaded: Boolean,
        val coreLoaded: Boolean,
        val wgVersion: String? = null,
        val cppVersion: String? = null,
        val selfTestPassed: Boolean? = null
    ) {
        val mode: Mode get() = decide(glueLoaded, coreLoaded)

        val reason: Reason
            get() = when {
                mode == Mode.REAL -> Reason.NONE
                coreLoaded -> Reason.GLUE_MISSING
                else -> Reason.CORE_MISSING
            }

        val isReal: Boolean get() = mode == Mode.REAL
    }

    /**
     * The truth table: REAL requires BOTH libraries. A resident core with a
     * missing glue would crash on the very first external call (the JNI
     * symbols live in the glue), so it must never be reported as REAL.
     */
    fun decide(glueLoaded: Boolean, coreLoaded: Boolean): Mode =
        if (glueLoaded && coreLoaded) Mode.REAL else Mode.SIMULATION

    /** Returns a copy of [snapshot] with the self-test verdict stamped in. */
    fun withSelfTest(snapshot: Snapshot, passed: Boolean): Snapshot =
        snapshot.copy(selfTestPassed = passed)

    /** Returns a copy of [snapshot] with the reported backend versions stamped in. */
    fun withVersions(snapshot: Snapshot, wg: String?, cpp: String?): Snapshot =
        snapshot.copy(wgVersion = wg, cppVersion = cpp)
}
