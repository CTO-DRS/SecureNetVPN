package com.securenet.vpn

import com.securenet.vpn.intelligence.SpeedTestEngine
import com.securenet.vpn.ui.splittunnel.SplitTunnelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTunnelAndSpeedTestTest {

    // ---- SplitTunnelStore ------------------------------------------------

    @Test
    fun `split policy round-trips through JSON`() {
        val policy = SplitTunnelStore.Policy(
            SplitTunnelStore.Mode.ALLOW_ONLY, setOf("com.a", "com.b")
        )
        val decoded = SplitTunnelStore.decode(SplitTunnelStore.encode(policy), 1)
        assertEquals(SplitTunnelStore.Mode.ALLOW_ONLY, decoded.mode)
        assertEquals(setOf("com.a", "com.b"), decoded.packages)
    }

    @Test
    fun `corrupt json decodes to empty policy`() {
        val decoded = SplitTunnelStore.decode("not-json{", 2)
        assertEquals(SplitTunnelStore.Mode.EXCLUDE, decoded.mode)
        assertTrue(decoded.packages.isEmpty())
    }

    @Test
    fun `mode int maps to enum`() {
        assertEquals(SplitTunnelStore.Mode.OFF, SplitTunnelStore.decode("[]", 0).mode)
        assertEquals(SplitTunnelStore.Mode.ALLOW_ONLY, SplitTunnelStore.decode("[]", 1).mode)
        assertEquals(SplitTunnelStore.Mode.EXCLUDE, SplitTunnelStore.decode("[]", 2).mode)
        assertEquals(SplitTunnelStore.Mode.OFF, SplitTunnelStore.decode("[]", 99).mode)
    }

    @Test
    fun `appliesTo decision table`() {
        val allow = SplitTunnelStore.Policy(SplitTunnelStore.Mode.ALLOW_ONLY, setOf("com.a"))
        val exclude = SplitTunnelStore.Policy(SplitTunnelStore.Mode.EXCLUDE, setOf("com.a"))
        val off = SplitTunnelStore.Policy(SplitTunnelStore.Mode.OFF, emptySet())

        assertTrue(SplitTunnelStore.appliesTo(allow, "com.a"))
        assertFalse(SplitTunnelStore.appliesTo(allow, "com.b"))
        assertFalse(SplitTunnelStore.appliesTo(exclude, "com.a"))
        assertTrue(SplitTunnelStore.appliesTo(exclude, "com.b"))
        assertTrue(SplitTunnelStore.appliesTo(off, "anything"))
    }

    // ---- SpeedTestEngine (pure serialization) ----------------------------

    @Test
    fun `speed result round-trips through JSON`() {
        val result = SpeedTestEngine.Result(
            pingMs = 23, downloadMbps = 84.5, uploadMbps = 21.3,
            completedAtMillis = 1_700_000_000_000
        )
        val decoded = SpeedTestEngine.Result.fromJson(result.toJson())
        assertEquals(result, decoded)
    }

    @Test
    fun `speed history caps at 10 and sorts newest first`() {
        var history = SpeedTestEngine.History(emptyList())
        repeat(14) { i ->
            history = SpeedTestEngine.History.appended(
                history,
                SpeedTestEngine.Result(i, 1.0, 1.0, 1_700_000_000_000L + i)
            )
        }
        assertEquals(10, history.results.size)
        assertTrue(history.results.first().pingMs == 13)
        assertTrue(history.results.last().pingMs == 4)
    }

    @Test
    fun `corrupt history json yields empty history`() {
        val history = SpeedTestEngine.History.fromJson("{{{")
        assertEquals(0, history.results.size)
    }
}
