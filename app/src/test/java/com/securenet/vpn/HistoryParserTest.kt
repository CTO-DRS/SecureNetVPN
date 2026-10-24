package com.securenet.vpn

import com.securenet.vpn.data.local.HistoryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryParserTest {

    private val parser = HistoryParser()

    private fun ts(millis: Long) = formatTs(millis)

    private fun formatTs(millis: Long): String {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = millis
        }
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US)
        return fmt.format(cal.time)
    }

    private fun line(event: String, millis: Long, server: String? = null, detail: String? = null): String =
        buildString {
            append("{\"ts\":\"").append(formatTs(millis)).append('"')
            append(",\"event\":\"").append(event).append('"')
            if (server != null) append(",\"server\":\"").append(server).append('"')
            if (detail != null) append(",\"detail\":\"").append(detail).append('"')
            append("}")
        }

    @Test
    fun `pairs connected and disconnected into a session`() {
        val timeline = parser.parse(
            listOf(
                line("connected", 1_000L, "de-1"),
                line("disconnected", 61_000L, "de-1", "user_action")
            )
        )
        assertEquals(1, timeline.sessions.size)
        val s = timeline.sessions.first()
        assertEquals("de-1", s.serverId)
        assertEquals(60_000L, s.durationMillis)
        assertEquals("disconnected", s.endEvent)
        assertEquals("user_action", s.endDetail)
        assertFalse(s.isOpen)
    }

    @Test
    fun `failed closing event marks failed session`() {
        val timeline = parser.parse(
            listOf(
                line("connected", 1_000L, "us-2"),
                line("failed", 2_000L, "us-2", "wg_turn_on_failed")
            )
        )
        assertEquals("failed", timeline.sessions.first().endEvent)
    }

    @Test
    fun `open session without close stays open`() {
        val timeline = parser.parse(listOf(line("connected", 5_000L, "fr-1")))
        val s = timeline.sessions.single()
        assertTrue(s.isOpen)
        assertEquals("", s.endEvent)
    }

    @Test
    fun `reconnecting events do not break pairing`() {
        val timeline = parser.parse(
            listOf(
                line("connected", 0L, "de-1"),
                line("reconnecting", 10_000L, "de-1", "network_change"),
                line("disconnected", 20_000L, "de-1")
            )
        )
        assertEquals(1, timeline.sessions.size)
        assertEquals(20_000L, timeline.sessions.first().durationMillis)
    }

    @Test
    fun `sessions sorted newest first`() {
        val timeline = parser.parse(
            listOf(
                line("connected", 100L, "a"),
                line("disconnected", 200L, "a"),
                line("connected", 300L, "b"),
                line("disconnected", 400L, "b")
            )
        )
        assertEquals("b", timeline.sessions.first().serverId)
        assertEquals("a", timeline.sessions.last().serverId)
    }

    @Test
    fun `usage entries aggregate per day`() {
        val timeline = parser.parse(
            listOf(
                usageLine(100L, 500L, 200L, 60L),
                usageLine(100L, 500L, 300L, 120L),
                usageLine(101L, 1L, 1L, 1L)
            )
        )
        assertEquals(2, timeline.usage.size)
        val day100 = timeline.usage.first { it.dayEpoch == 100L }
        assertEquals(1000L, day100.rxBytes)
        assertEquals(500L, day100.txBytes)
        assertEquals(180L, day100.connectedSeconds)
        assertEquals(101L, timeline.usage.first().dayEpoch) // newest day first (descending)
    }

    @Test
    fun `malformed lines are ignored`() {
        val timeline = parser.parse(
            listOf("", "not json", "{", line("connected", 0L, "de-1"))
        )
        assertEquals(1, timeline.totalEvents)
        assertEquals(1, timeline.sessions.size)
    }

    @Test
    fun `detail escapes are unescaped`() {
        val timeline = parser.parse(
            listOf(line("disconnected", 10L, detail = "reason \"quoted\" path\\\\x"))
        )
        // Standalone close without open → no session, but event recorded.
        assertEquals(1, timeline.totalEvents)
        assertTrue(timeline.sessions.isEmpty())
    }

    @Test
    fun `missing server field yields null server`() {
        val timeline = parser.parse(listOf(line("connected", 0L)))
        assertNull(timeline.sessions.single().serverId)
    }

    @Test
    fun `events counter counts non-usage events`() {
        val timeline = parser.parse(
            listOf(
                line("connected", 0L, "de-1"),
                line("disconnected", 1L, "de-1"),
                usageLine(1L, 1L, 1L, 1L)
            )
        )
        assertEquals(2, timeline.totalEvents)
    }

    private fun usageLine(day: Long, rx: Long, tx: Long, secs: Long): String =
        "{\"ts\":\"" + formatTs(0L) + "\",\"event\":\"usage\",\"day\":" + day +
            ",\"rx\":" + rx + ",\"tx\":" + tx + ",\"connected_s\":" + secs + "}"
}
