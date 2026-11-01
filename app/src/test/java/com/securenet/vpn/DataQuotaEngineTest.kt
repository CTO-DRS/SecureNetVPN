package com.securenet.vpn

import com.securenet.vpn.stats.DataQuotaEngine
import com.securenet.vpn.stats.DataQuotaEngine.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.24 — the pure monthly data-quota ladder. Pins the three-level
 * policy (NONE / WARN at 80% / EXCEEDED at 100%), the advance-only alert
 * rule, the display percent clamps, the remaining-bytes honesty and the
 * preset round-trips — the exact contract [com.securenet.vpn.service
 * .QuotaAlertNotifier] and the settings sheet rely on.
 */
class DataQuotaEngineTest {

    private val GB = 1L shl 30

    // ------------------------------------------------------------------
    // evaluate — the feature-off path
    // ------------------------------------------------------------------

    @Test
    fun `non-positive quota means the feature is off`() {
        val state = DataQuotaEngine.evaluate(usedBytes = 900L, quotaBytes = 0L)
        assertEquals(Level.NONE, state.level)
        assertEquals(0, state.percent)
        assertEquals(0L, state.remainingBytes)
        assertEquals(0L, state.quotaBytes)
    }

    @Test
    fun `negative quota is treated as off too`() {
        assertEquals(Level.NONE, DataQuotaEngine.evaluate(100L, -5L).level)
    }

    // ------------------------------------------------------------------
    // evaluate — the ladder
    // ------------------------------------------------------------------

    @Test
    fun `zero usage reads none at zero percent`() {
        val state = DataQuotaEngine.evaluate(0L, 5 * GB)
        assertEquals(Level.NONE, state.level)
        assertEquals(0, state.percent)
        assertEquals(5 * GB, state.remainingBytes)
    }

    @Test
    fun `small usage stays none below the warn line`() {
        val state = DataQuotaEngine.evaluate(GB, 5 * GB) // 20%
        assertEquals(Level.NONE, state.level)
        assertEquals(20, state.percent)
    }

    @Test
    fun `exactly the warn percent flips to warn`() {
        val state = DataQuotaEngine.evaluate(4 * GB, 5 * GB) // exactly 80%
        assertEquals(Level.WARN, state.level)
        assertEquals(80, state.percent)
    }

    @Test
    fun `between warn and cap stays warn`() {
        val state = DataQuotaEngine.evaluate(4 * GB + 1, 5 * GB) // 80%+ε
        assertEquals(Level.WARN, state.level)
    }

    @Test
    fun `exactly the cap is exceeded with zero remaining`() {
        val state = DataQuotaEngine.evaluate(5 * GB, 5 * GB)
        assertEquals(Level.EXCEEDED, state.level)
        assertEquals(100, state.percent)
        assertEquals(0L, state.remainingBytes)
    }

    @Test
    fun `over the cap clamps the display percent and remaining`() {
        val state = DataQuotaEngine.evaluate(8 * GB, 5 * GB) // 160% used
        assertEquals(Level.EXCEEDED, state.level)
        assertEquals(100, state.percent)
        assertEquals(0L, state.remainingBytes)
        assertEquals(8 * GB, state.usedBytes)
    }

    @Test
    fun `negative usage is clamped to zero`() {
        val state = DataQuotaEngine.evaluate(-42L, GB)
        assertEquals(Level.NONE, state.level)
        assertEquals(0L, state.usedBytes)
        assertEquals(GB, state.remainingBytes)
    }

    @Test
    fun `custom warn percent moves the warn line`() {
        // Exactly 50% of 1 GB: the default 80% line reads NONE, a 50% line
        // reads WARN (the floored percent hits the boundary exactly).
        val used = GB / 2
        assertEquals(50, DataQuotaEngine.evaluate(used, GB).percent)
        assertEquals(Level.NONE, DataQuotaEngine.evaluate(used, GB).level)
        assertEquals(Level.WARN, DataQuotaEngine.evaluate(used, GB, warnPercent = 50).level)
    }

    @Test
    fun `warn percent is coerced into a sane range`() {
        // 0% must not warn on empty traffic; 1000% must not swallow the cap.
        assertEquals(Level.NONE, DataQuotaEngine.evaluate(1L, GB, warnPercent = 0).level)
        assertEquals(
            Level.EXCEEDED,
            DataQuotaEngine.evaluate(GB, GB, warnPercent = 1000).level
        )
    }

    @Test
    fun `percent floors rather than rounds`() {
        // 79.9% (of 10 GB) must not display or warn as 80%.
        val used = 799L * (10L * GB) / 1000L
        val state = DataQuotaEngine.evaluate(used, 10L * GB)
        assertEquals(Level.NONE, state.level)
        assertEquals(79, state.percent)
    }

    // ------------------------------------------------------------------
    // shouldAlert — advance-only notifications
    // ------------------------------------------------------------------

    @Test
    fun `none to warn alerts`() {
        assertTrue(DataQuotaEngine.shouldAlert(0, Level.WARN))
    }

    @Test
    fun `warn to exceeded alerts`() {
        assertTrue(DataQuotaEngine.shouldAlert(1, Level.EXCEEDED))
    }

    @Test
    fun `straight jump from none to exceeded alerts`() {
        assertTrue(DataQuotaEngine.shouldAlert(0, Level.EXCEEDED))
    }

    @Test
    fun `same level never re-alerts`() {
        assertFalse(DataQuotaEngine.shouldAlert(1, Level.WARN))
        assertFalse(DataQuotaEngine.shouldAlert(2, Level.EXCEEDED))
    }

    @Test
    fun `regression never re-alerts`() {
        assertFalse(DataQuotaEngine.shouldAlert(2, Level.WARN))
        assertFalse(DataQuotaEngine.shouldAlert(1, Level.NONE))
    }

    @Test
    fun `staying none is silent`() {
        assertFalse(DataQuotaEngine.shouldAlert(0, Level.NONE))
    }

    // ------------------------------------------------------------------
    // Level + presets
    // ------------------------------------------------------------------

    @Test
    fun `unknown stored levels degrade to none`() {
        assertEquals(Level.NONE, Level.from(-3))
        assertEquals(Level.NONE, Level.from(99))
        assertEquals(Level.WARN, Level.from(1))
        assertEquals(Level.EXCEEDED, Level.from(2))
    }

    @Test
    fun `preset bytes use the binary gigabyte`() {
        assertEquals(1_073_741_824L, DataQuotaEngine.Preset.GB1.bytes)
        assertEquals(5L * GB, DataQuotaEngine.Preset.GB5.bytes)
        assertEquals(10L * GB, DataQuotaEngine.Preset.GB10.bytes)
        assertEquals(50L * GB, DataQuotaEngine.Preset.GB50.bytes)
        assertEquals(0L, DataQuotaEngine.Preset.OFF.bytes)
    }

    @Test
    fun `stored cap round-trips to its preset and unknown caps read off`() {
        assertEquals(DataQuotaEngine.Preset.GB5, DataQuotaEngine.Preset.fromBytes(5L * GB))
        assertEquals(DataQuotaEngine.Preset.OFF, DataQuotaEngine.Preset.fromBytes(0L))
        // A hypothetical custom cap has no preset — reads OFF, never crashes.
        assertEquals(DataQuotaEngine.Preset.OFF, DataQuotaEngine.Preset.fromBytes(2L * GB + 7))
    }

    @Test
    fun `default warn line is eighty percent`() {
        assertEquals(80, DataQuotaEngine.DEFAULT_WARN_PERCENT)
    }
}
