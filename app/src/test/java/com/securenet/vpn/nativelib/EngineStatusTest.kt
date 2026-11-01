package com.securenet.vpn.nativelib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.25 — the engine card's pure decision layer. The truth table is the
 * contract: REAL requires BOTH native libraries (a core without its JNI
 * glue would crash on the first external call, so it must never read as
 * real); the reason ladder names the missing piece in audit order; the
 * snapshot stays coherent as self-test verdicts and versions are stamped
 * in. Everything here is plain JVM data — no android imports.
 */
class EngineStatusTest {

    // ------------------------------------------------------------------
    // decide(): the truth table
    // ------------------------------------------------------------------

    @Test
    fun `both libraries resident means REAL`() {
        assertEquals(
            EngineStatus.Mode.REAL,
            EngineStatus.decide(glueLoaded = true, coreLoaded = true)
        )
    }

    @Test
    fun `core without glue is never REAL`() {
        // The JNI symbols live in the glue — a resident core alone would
        // UnsatisfiedLinkError on the first wgTurnOn call.
        assertEquals(
            EngineStatus.Mode.SIMULATION,
            EngineStatus.decide(glueLoaded = false, coreLoaded = true)
        )
    }

    @Test
    fun `glue without core is simulation`() {
        assertEquals(
            EngineStatus.Mode.SIMULATION,
            EngineStatus.decide(glueLoaded = true, coreLoaded = false)
        )
    }

    @Test
    fun `nothing loaded is simulation`() {
        assertEquals(
            EngineStatus.Mode.SIMULATION,
            EngineStatus.decide(glueLoaded = false, coreLoaded = false)
        )
    }

    // ------------------------------------------------------------------
    // Snapshot: mode, reason ladder, coherence
    // ------------------------------------------------------------------

    @Test
    fun `snapshot mode mirrors the truth table`() {
        val real = EngineStatus.Snapshot(glueLoaded = true, coreLoaded = true)
        val simulated = EngineStatus.Snapshot(glueLoaded = true, coreLoaded = false)
        assertTrue(real.isReal)
        assertFalse(simulated.isReal)
        assertEquals(EngineStatus.Mode.REAL, real.mode)
        assertEquals(EngineStatus.Mode.SIMULATION, simulated.mode)
    }

    @Test
    fun `real snapshots have no reason`() {
        val real = EngineStatus.Snapshot(
            glueLoaded = true,
            coreLoaded = true,
            wgVersion = "wireguard-go 0.0.20230223 (securenet build)"
        )
        assertEquals(EngineStatus.Reason.NONE, real.reason)
    }

    @Test
    fun `missing core is audited before missing glue`() {
        val coreMissing = EngineStatus.Snapshot(glueLoaded = false, coreLoaded = false)
        val glueMissing = EngineStatus.Snapshot(glueLoaded = false, coreLoaded = true)
        assertEquals(EngineStatus.Reason.CORE_MISSING, coreMissing.reason)
        assertEquals(EngineStatus.Reason.GLUE_MISSING, glueMissing.reason)
    }

    // ------------------------------------------------------------------
    // Stamping: self-test verdicts and versions keep the snapshot coherent
    // ------------------------------------------------------------------

    @Test
    fun `cold snapshot has no self-test verdict`() {
        val cold = EngineStatus.Snapshot(glueLoaded = true, coreLoaded = true)
        assertNull(cold.selfTestPassed)
    }

    @Test
    fun `withSelfTest stamps the verdict without touching the mode`() {
        val before = EngineStatus.Snapshot(glueLoaded = true, coreLoaded = true)
        val after = EngineStatus.withSelfTest(before, passed = true)
        assertTrue(after.selfTestPassed == true)
        assertEquals(EngineStatus.Mode.REAL, after.mode)
    }

    @Test
    fun `failed self-test does not demote a real engine`() {
        // The libraries are resident; a KAT failure is alarming but the
        // engine card reports facts, not fear — mode stays REAL, the
        // verdict line carries the failure.
        val before = EngineStatus.Snapshot(glueLoaded = true, coreLoaded = true)
        val after = EngineStatus.withSelfTest(before, passed = false)
        assertEquals(EngineStatus.Mode.REAL, after.mode)
        assertTrue(after.selfTestPassed == false)
    }

    @Test
    fun `withVersions stamps both versions in one copy`() {
        val before = EngineStatus.Snapshot(glueLoaded = true, coreLoaded = true)
        val after = EngineStatus.withVersions(
            before,
            wg = "wireguard-go 0.0.20230223 (securenet build)",
            cpp = "securenet-crypto 1.0"
        )
        assertEquals("wireguard-go 0.0.20230223 (securenet build)", after.wgVersion)
        assertEquals("securenet-crypto 1.0", after.cppVersion)
    }

    @Test
    fun `simulated snapshot tolerates null versions`() {
        val simulated = EngineStatus.Snapshot(
            glueLoaded = false,
            coreLoaded = false,
            wgVersion = null,
            cppVersion = null
        )
        assertEquals(EngineStatus.Mode.SIMULATION, simulated.mode)
        assertNull(simulated.wgVersion)
        assertNull(simulated.cppVersion)
    }
}
