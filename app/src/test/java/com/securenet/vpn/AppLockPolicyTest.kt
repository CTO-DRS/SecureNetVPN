package com.securenet.vpn

import com.securenet.vpn.security.AppLockPolicy
import com.securenet.vpn.security.AppLockPolicy.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.24 — the pure app-lock decision. Pins the master-switch rule, the
 * fresh-install no-lock rule, the IMMEDIATE / 1 min / 5 min grace
 * windows (boundary-inclusive), the backwards-clock defense and the
 * stored-value fallbacks — the exact contract MainActivity's overlay
 * gate relies on.
 */
class AppLockPolicyTest {

    private val NOW = 1_000_000_000L

    // ------------------------------------------------------------------
    // master switch + fresh state
    // ------------------------------------------------------------------

    @Test
    fun `disabled never locks whatever the timing`() {
        assertFalse(
            AppLockPolicy.shouldLock(false, Timeout.IMMEDIATE, NOW - 1, NOW)
        )
        assertFalse(
            AppLockPolicy.shouldLock(false, Timeout.FIVE_MIN, 0L, NOW)
        )
    }

    @Test
    fun `no recorded pause never locks even when enabled`() {
        // Fresh install / first open after enabling: nothing to protect yet.
        assertFalse(AppLockPolicy.shouldLock(true, Timeout.IMMEDIATE, 0L, NOW))
        assertFalse(AppLockPolicy.shouldLock(true, Timeout.ONE_MIN, -1L, NOW))
    }

    // ------------------------------------------------------------------
    // IMMEDIATE
    // ------------------------------------------------------------------

    @Test
    fun `immediate locks after any elapsed pause`() {
        assertTrue(
            AppLockPolicy.shouldLock(true, Timeout.IMMEDIATE, NOW - 1, NOW)
        )
    }

    @Test
    fun `immediate locks on an exactly-now pause too`() {
        // delay 0 → the boundary is inclusive at zero elapsed.
        assertTrue(
            AppLockPolicy.shouldLock(true, Timeout.IMMEDIATE, NOW, NOW)
        )
    }

    // ------------------------------------------------------------------
    // grace windows
    // ------------------------------------------------------------------

    @Test
    fun `one minute window does not lock inside the minute`() {
        val paused = NOW - 59_999L
        assertFalse(AppLockPolicy.shouldLock(true, Timeout.ONE_MIN, paused, NOW))
    }

    @Test
    fun `one minute window locks at exactly one minute`() {
        val paused = NOW - 60_000L
        assertTrue(AppLockPolicy.shouldLock(true, Timeout.ONE_MIN, paused, NOW))
    }

    @Test
    fun `five minute window does not lock inside the window`() {
        val paused = NOW - 299_999L
        assertFalse(AppLockPolicy.shouldLock(true, Timeout.FIVE_MIN, paused, NOW))
    }

    @Test
    fun `five minute window locks at exactly five minutes`() {
        val paused = NOW - 300_000L
        assertTrue(AppLockPolicy.shouldLock(true, Timeout.FIVE_MIN, paused, NOW))
    }

    @Test
    fun `long past pauses lock under every timeout`() {
        val paused = NOW - 86_400_000L // a full day ago
        Timeout.entries.forEach { timeout ->
            assertTrue(
                "timeout $timeout must lock after a day",
                AppLockPolicy.shouldLock(true, timeout, paused, NOW)
            )
        }
    }

    // ------------------------------------------------------------------
    // clock defenses + fallbacks
    // ------------------------------------------------------------------

    @Test
    fun `a backwards clock never locks`() {
        // now earlier than the recorded pause — never trap the user.
        assertFalse(
            AppLockPolicy.shouldLock(true, Timeout.IMMEDIATE, NOW + 5_000L, NOW)
        )
    }

    @Test
    fun `unknown stored timeout falls back to immediate`() {
        assertEquals(Timeout.IMMEDIATE, Timeout.from(-1))
        assertEquals(Timeout.IMMEDIATE, Timeout.from(99))
    }

    @Test
    fun `stored timeout round-trips`() {
        assertEquals(Timeout.IMMEDIATE, Timeout.from(0))
        assertEquals(Timeout.ONE_MIN, Timeout.from(1))
        assertEquals(Timeout.FIVE_MIN, Timeout.from(2))
    }

    @Test
    fun `grace windows carry their advertised millis`() {
        assertEquals(0L, Timeout.IMMEDIATE.delayMillis)
        assertEquals(60_000L, Timeout.ONE_MIN.delayMillis)
        assertEquals(300_000L, Timeout.FIVE_MIN.delayMillis)
    }
}
