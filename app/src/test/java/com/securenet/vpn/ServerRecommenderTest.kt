package com.securenet.vpn

import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.intelligence.ServerRecommender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRecommenderTest {

    private val recommender = ServerRecommender()

    private fun server(id: String, latency: Int, load: Int) = VpnServer(
        id = id, name = id, countryCode = "DE", city = id,
        endpointHost = "$id.example.com", publicKey = "key-$id",
        latencyMs = latency, loadPercent = load
    )

    @Test
    fun `empty list yields no recommendation`() {
        assertNull(recommender.recommend(emptyList()))
    }

    @Test
    fun `fast unloaded server wins over slow loaded one`() {
        val fast = server("fast", latency = 25, load = 10)
        val slow = server("slow", latency = 250, load = 90)
        val best = recommender.recommend(listOf(slow, fast))
        assertEquals("fast", best?.server?.id)
    }

    @Test
    fun `live latency overrides reported latency`() {
        val a = server("a", latency = 20, load = 0)
        val b = server("b", latency = 300, load = 0)
        // Live probe says "a" is actually terrible right now.
        val best = recommender.recommend(listOf(a, b), liveLatencies = mapOf("a" to 400))
        assertEquals("b", best?.server?.id)
    }

    @Test
    fun `stickiness keeps last used server on ties`() {
        val a = server("a", latency = 100, load = 40)
        val b = server("b", latency = 100, load = 40)
        val best = recommender.recommend(listOf(a, b), lastUsedId = "b")
        assertEquals("b", best?.server?.id)
        assertTrue(best?.sticky == true)
    }

    @Test
    fun `switch is not suggested when current is close enough`() {
        val current = server("current", latency = 80, load = 30)
        val other = server("other", latency = 75, load = 28)
        assertFalse(
            recommender.shouldSuggestSwitch(current, listOf(current, other),
                lastUsedId = "current")
        )
    }

    @Test
    fun `switch is suggested when a clearly better server exists`() {
        val current = server("current", latency = 400, load = 95)
        val better = server("better", latency = 30, load = 5)
        assertTrue(
            recommender.shouldSuggestSwitch(current, listOf(current, better),
                lastUsedId = "current")
        )
    }

    @Test
    fun `score stays in bounded range`() {
        val s = recommender.score(server("x", latency = 1000, load = 100))
        assertTrue(s >= 0.0 && s <= 1.5)
        // 0.6 * 1/(1+1000/120) + 0.4*0 = 0.0643 (no stickiness for unknown id)
        assertEquals(0.0643, recommender.score(server("y", latency = 1000, load = 100)),
            0.001)
    }
}
