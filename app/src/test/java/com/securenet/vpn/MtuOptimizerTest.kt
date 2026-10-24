package com.securenet.vpn

import com.securenet.vpn.network.MtuOptimizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MtuOptimizerTest {

    private val optimizer = MtuOptimizer()

    @Test
    fun `recommended MTU for 1500 link IPv4 is capped at 1420`() {
        // 1500 - 60 (IPv4 overhead) - 16 (safety margin) = 1424,
        // which the WireGuard tunnel cap (MAX_TUNNEL_MTU = 1420) clamps.
        assertEquals(1420, optimizer.recommendedMtu(1500, useIpv6 = false))
    }

    @Test
    fun `recommended MTU for 1500 link IPv6 is 1404`() {
        // 1500 - 80 (IPv6 overhead) - 16 (safety margin)
        assertEquals(1404, optimizer.recommendedMtu(1500, useIpv6 = true))
    }

    @Test
    fun `never drops below 1280`() {
        assertEquals(1280, optimizer.recommendedMtu(1300, useIpv6 = true))
        assertEquals(1280, optimizer.recommendedMtu(1000, useIpv6 = false))
    }

    @Test
    fun `never exceeds wireguard tunnel cap of 1420`() {
        val mtu = optimizer.recommendedMtu(9000, useIpv6 = false)
        assertTrue(mtu <= MtuOptimizer.MAX_TUNNEL_MTU)
    }

    @Test
    fun `clamp enforces the valid range`() {
        assertEquals(1280, optimizer.clamp(900))
        assertEquals(1500, optimizer.clamp(2000))
        assertEquals(1420, optimizer.clamp(1420))
    }

    @Test
    fun `battery saver caps MTU at 1380`() {
        assertEquals(1380, optimizer.applyPolicy(1420, batterySaver = true))
        assertEquals(1420, optimizer.applyPolicy(1420, batterySaver = false))
    }

    @Test
    fun `binary search probe converges on a working MTU`() {
        var state = MtuOptimizer.ProbeState(lowerBound = 1280, upperBound = 1420)

        // First call without a result just picks the midpoint.
        state = optimizer.nextProbe(state, success = null)
        assertEquals(1350, state.currentProbe)
        assertFalse(state.converged)

        // Simulate a path that supports 1400 but not 1415.
        val realMax = 1400
        var iterations = 0
        while (!state.converged && iterations < 20) {
            val success = state.currentProbe <= realMax
            state = optimizer.nextProbe(state, success)
            iterations++
        }
        assertTrue("probe must converge", state.converged)
        assertTrue(
            "converged MTU should be near the real maximum",
            state.currentProbe >= realMax - MtuOptimizer.CONVERGENCE_EPSILON &&
                state.currentProbe <= realMax
        )
    }

    @Test
    fun `probe fails fast when everything fails`() {
        var state = MtuOptimizer.ProbeState()
        state = optimizer.nextProbe(state, success = null)
        var iterations = 0
        while (!state.converged && iterations < 20) {
            state = optimizer.nextProbe(state, success = false)
            iterations++
        }
        assertTrue(state.converged)
        assertTrue(state.currentProbe >= MtuOptimizer.MIN_MTU)
    }
}
