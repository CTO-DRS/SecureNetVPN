package com.securenet.vpn

import com.securenet.vpn.data.local.HistoryParser
import com.securenet.vpn.stats.ServerStatsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.6 — Per-server statistics aggregation over the parsed session
 * timeline: grouping, duration sums, failure counts, ordering and the
 * share-of-time bars shown in the dialog.
 */
class ServerStatsEngineTest {

    private fun session(
        server: String?,
        start: Long,
        end: Long,
        endEvent: String = "disconnected"
    ) = HistoryParser.Session(
        serverId = server,
        startedAtMillis = start,
        endedAtMillis = end,
        endEvent = endEvent,
        endDetail = null
    )

    @Test
    fun `empty timeline yields empty usage`() {
        assertTrue(ServerStatsEngine.compute(emptyList()).isEmpty())
    }

    @Test
    fun `sessions group per server with duration and failure sums`() {
        val hour = 3_600_000L
        val usages = ServerStatsEngine.compute(
            listOf(
                session("de-1", 0, hour),
                session("de-1", 10, 10 + 2 * hour, "failed"),
                session("nl-2", 20, 20 + hour / 2)
            )
        )

        assertEquals(2, usages.size)

        val de = usages.first { it.serverId == "de-1" }
        assertEquals(2, de.sessions)
        assertEquals(3 * hour / 1000, de.connectedSeconds)
        assertEquals(1, de.failures)
        assertEquals(10 + 2 * hour, de.lastUsedMillis)

        val nl = usages.first { it.serverId == "nl-2" }
        assertEquals(1, nl.sessions)
        assertEquals(hour / 2 / 1000, nl.connectedSeconds)
        assertEquals(0, nl.failures)
    }

    @Test
    fun `open sessions contribute zero connected seconds`() {
        val usages = ServerStatsEngine.compute(
            listOf(session("de-1", 1_000L, 1_000L, endEvent = ""))
        )
        assertEquals(1, usages.size)
        assertEquals(0L, usages[0].connectedSeconds)
        assertEquals(1, usages[0].sessions)
    }

    @Test
    fun `servers without id are skipped`() {
        val usages = ServerStatsEngine.compute(
            listOf(session(null, 0, 100), session("de-1", 0, 100))
        )
        assertEquals(1, usages.size)
        assertEquals("de-1", usages[0].serverId)
    }

    @Test
    fun `ordering is most connected time first then sessions then recency`() {
        val hour = 3_600_000L
        val usages = ServerStatsEngine.compute(
            listOf(
                session("nl-2", 0, hour),
                session("de-1", 0, 4 * hour),
                session("fr-3", 0, hour),
                session("fr-3", hour, 2 * hour)
            )
        )

        assertEquals("de-1", usages[0].serverId)
        assertEquals("fr-3", usages[1].serverId)
        assertEquals("nl-2", usages[2].serverId)
    }

    @Test
    fun `share of time splits evenly for equal usage`() {
        val hour = 3_600_000L
        val usages = ServerStatsEngine.compute(
            listOf(session("de-1", 0, hour), session("nl-2", 0, hour))
        )
        assertEquals(50, ServerStatsEngine.shareSeconds(usages[0], usages))
        assertEquals(50, ServerStatsEngine.shareSeconds(usages[1], usages))
    }

    @Test
    fun `share is zero when nothing recorded`() {
        val usages = ServerStatsEngine.compute(
            listOf(session("de-1", 100L, 100L, endEvent = ""))
        )
        assertEquals(0, ServerStatsEngine.shareSeconds(usages[0], usages))
    }

    @Test
    fun `display name falls back to the raw id`() {
        val names = mapOf("de-1" to "Frankfurt")
        assertEquals("Frankfurt", ServerStatsEngine.displayName("de-1", names))
        assertEquals("de-1", ServerStatsEngine.displayName("de-1", emptyMap()))
        assertEquals("de-1", ServerStatsEngine.displayName("de-1", mapOf("de-1" to "")))
    }
}
