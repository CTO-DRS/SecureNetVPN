package com.securenet.vpn

import com.securenet.vpn.schedule.ConnectionSchedule
import com.securenet.vpn.schedule.ConnectionScheduleEngine
import com.securenet.vpn.schedule.ScheduleAction
import com.securenet.vpn.tor.TorMode
import com.securenet.vpn.tor.TorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.3: Tor-over-VPN + connection-scheduling logic tests.
 */
class TorAndScheduleTest {

    // ------------------------------------------------------------------
    // TorMode.parseStatus
    // ------------------------------------------------------------------

    @Test
    fun `orbot status payloads map to tor states`() {
        assertEquals(TorStatus.CONNECTED, TorMode.parseStatus("ON"))
        assertEquals(TorStatus.CONNECTED, TorMode.parseStatus("on"))
        assertEquals(TorStatus.STARTING, TorMode.parseStatus("STARTING"))
        assertEquals(TorStatus.OFF, TorMode.parseStatus("OFF"))
        assertEquals(TorStatus.OFF, TorMode.parseStatus("STOPPING"))
    }

    @Test
    fun `unknown orbot payload never reads as connected`() {
        assertEquals(TorStatus.FAILED, TorMode.parseStatus(null))
        assertEquals(TorStatus.FAILED, TorMode.parseStatus(""))
        assertEquals(TorStatus.FAILED, TorMode.parseStatus("GARBAGE"))
    }

    // ------------------------------------------------------------------
    // TorMode.parseSocksEndpoint — loopback enforcement
    // ------------------------------------------------------------------

    @Test
    fun `socks endpoint from orbot is accepted on loopback`() {
        val endpoint = TorMode.parseSocksEndpoint("socks://127.0.0.1:9050")
        assertEquals("127.0.0.1" to 9050, endpoint)
    }

    @Test
    fun `remote socks host is rejected to prevent traffic leak`() {
        assertNull(TorMode.parseSocksEndpoint("socks://10.0.0.5:9050"))
        assertNull(TorMode.parseSocksEndpoint("socks://8.8.8.8:1080"))
    }

    @Test
    fun `malformed socks payload is rejected`() {
        assertNull(TorMode.parseSocksEndpoint(null))
        assertNull(TorMode.parseSocksEndpoint("no-scheme"))
        assertNull(TorMode.parseSocksEndpoint("socks://127.0.0.1:notaport"))
        assertNull(TorMode.parseSocksEndpoint("socks://127.0.0.1:70000"))
    }

    // ------------------------------------------------------------------
    // TorMode.decide — the service decision matrix
    // ------------------------------------------------------------------

    @Test
    fun `decision matrix covers all tor branches`() {
        assertEquals(
            TorMode.Action.TOR_OFF,
            TorMode.decide(enabled = false, installed = true, status = TorStatus.CONNECTED)
        )
        assertEquals(
            TorMode.Action.NEED_ORBOT,
            TorMode.decide(enabled = true, installed = false, status = TorStatus.OFF)
        )
        assertEquals(
            TorMode.Action.WAITING,
            TorMode.decide(enabled = true, installed = true, status = TorStatus.STARTING)
        )
        assertEquals(
            TorMode.Action.ACTIVE,
            TorMode.decide(enabled = true, installed = true, status = TorStatus.CONNECTED)
        )
        // Waiting persists when Orbot exists but Tor was never turned on.
        assertEquals(
            TorMode.Action.WAITING,
            TorMode.decide(enabled = true, installed = true, status = TorStatus.OFF)
        )
    }

    @Test
    fun `socks port probe downgrade keeps honest state`() {
        assertEquals(
            TorStatus.CONNECTED,
            TorMode.resolveStatus(TorStatus.CONNECTED, socksPortOpen = true)
        )
        assertEquals(
            TorStatus.STARTING,
            TorMode.resolveStatus(TorStatus.CONNECTED, socksPortOpen = false)
        )
        assertEquals(TorStatus.OFF, TorMode.resolveStatus(TorStatus.OFF, socksPortOpen = true))
    }

    @Test
    fun `loopback host check`() {
        assertTrue(TorMode.isLoopback("127.0.0.1"))
        assertTrue(TorMode.isLoopback("localhost"))
        assertTrue(TorMode.isLoopback("::1"))
        assertFalse(TorMode.isLoopback("192.168.1.1"))
    }

    // ------------------------------------------------------------------
    // ConnectionScheduleEngine time parsing
    // ------------------------------------------------------------------

    @Test
    fun `time parsing accepts valid clock values only`() {
        assertEquals(22 * 60, ConnectionScheduleEngine.toMinutes("22:00"))
        assertEquals(0, ConnectionScheduleEngine.toMinutes("00:00"))
        assertEquals(23 * 60 + 59, ConnectionScheduleEngine.toMinutes("23:59"))
        assertNull(ConnectionScheduleEngine.toMinutes("24:00"))
        assertNull(ConnectionScheduleEngine.toMinutes("7:60"))
        assertNull(ConnectionScheduleEngine.toMinutes("abc"))
        assertNull(ConnectionScheduleEngine.toMinutes("22"))
        assertTrue(ConnectionScheduleEngine.isValid("06:30"))
        assertFalse(ConnectionScheduleEngine.isValid("25:00"))
    }

    // ------------------------------------------------------------------
    // Same-day window: 09:00 -> 18:00
    // ------------------------------------------------------------------

    private val daySchedule = ConnectionSchedule(
        enabled = true, connectAt = "09:00", disconnectAt = "18:00",
        daysMask = ConnectionSchedule.ALL_DAYS
    )

    @Test
    fun `same-day window connects inside and disconnects outside`() {
        val monday = 1
        assertEquals(
            ScheduleAction.CONNECT,
            ConnectionScheduleEngine.evaluate(daySchedule, monday, 12 * 60, connected = false)
        )
        assertEquals(
            ScheduleAction.DISCONNECT,
            ConnectionScheduleEngine.evaluate(daySchedule, monday, 19 * 60, connected = true)
        )
        assertEquals(
            ScheduleAction.NONE,
            ConnectionScheduleEngine.evaluate(daySchedule, monday, 12 * 60, connected = true)
        )
        assertEquals(
            ScheduleAction.NONE,
            ConnectionScheduleEngine.evaluate(daySchedule, monday, 19 * 60, connected = false)
        )
    }

    @Test
    fun `window boundaries are half-open`() {
        val monday = 1
        // 09:00 exactly -> inside.
        assertEquals(
            ScheduleAction.CONNECT,
            ConnectionScheduleEngine.evaluate(daySchedule, monday, 9 * 60, connected = false)
        )
        // 18:00 exactly -> outside.
        assertEquals(
            ScheduleAction.DISCONNECT,
            ConnectionScheduleEngine.evaluate(daySchedule, monday, 18 * 60, connected = true)
        )
    }

    // ------------------------------------------------------------------
    // Midnight-wrapping window: 22:00 -> 07:00
    // ------------------------------------------------------------------

    private val nightSchedule = ConnectionSchedule(
        enabled = true, connectAt = "22:00", disconnectAt = "07:00",
        daysMask = ConnectionSchedule.ALL_DAYS
    )

    @Test
    fun `midnight window covers late evening and early morning`() {
        val monday = 1
        assertEquals(
            ScheduleAction.CONNECT,
            ConnectionScheduleEngine.evaluate(nightSchedule, monday, 23 * 60, connected = false)
        )
        assertEquals(
            ScheduleAction.CONNECT,
            ConnectionScheduleEngine.evaluate(nightSchedule, monday, 5 * 60, connected = false)
        )
        assertEquals(
            ScheduleAction.DISCONNECT,
            ConnectionScheduleEngine.evaluate(nightSchedule, monday, 8 * 60, connected = true)
        )
        assertEquals(
            ScheduleAction.DISCONNECT,
            ConnectionScheduleEngine.evaluate(nightSchedule, monday, 12 * 60, connected = true)
        )
    }

    @Test
    fun `midnight tail inherits previous day mask`() {
        // Friday (5) 22:00 -> Saturday (6) 07:00 window: Saturday 03:00 is
        // inside only when Friday's bit is set.
        val friSat = ConnectionSchedule(
            enabled = true, connectAt = "22:00", disconnectAt = "07:00",
            daysMask = (1 shl 4) or (1 shl 5) // Friday + Saturday
        )
        assertEquals(
            ScheduleAction.CONNECT,
            ConnectionScheduleEngine.evaluate(friSat, 6, 3 * 60, connected = false)
        )
        // Without Friday's bit, Saturday 03:00 is outside the window.
        val satOnly = ConnectionSchedule(
            enabled = true, connectAt = "22:00", disconnectAt = "07:00",
            daysMask = 1 shl 5
        )
        assertEquals(
            ScheduleAction.DISCONNECT,
            ConnectionScheduleEngine.evaluate(satOnly, 6, 3 * 60, connected = true)
        )
    }

    // ------------------------------------------------------------------
    // Day masking
    // ------------------------------------------------------------------

    @Test
    fun `inactive days never trigger actions`() {
        val weekdays = ConnectionSchedule(
            enabled = true, connectAt = "09:00", disconnectAt = "18:00",
            daysMask = ConnectionSchedule.WEEKDAYS
        )
        val saturday = 6
        assertEquals(
            ScheduleAction.DISCONNECT,
            ConnectionScheduleEngine.evaluate(weekdays, saturday, 12 * 60, connected = true)
        )
        assertEquals(
            ScheduleAction.NONE,
            ConnectionScheduleEngine.evaluate(weekdays, saturday, 12 * 60, connected = false)
        )
    }

    @Test
    fun `disabled schedule is a no-op even when connected`() {
        val off = ConnectionSchedule(enabled = false)
        assertEquals(
            ScheduleAction.NONE,
            ConnectionScheduleEngine.evaluate(off, 1, 12 * 60, connected = true)
        )
    }

    @Test
    fun `malformed schedule times degrade to no-op`() {
        val broken = ConnectionSchedule(
            enabled = true, connectAt = "99:99", disconnectAt = "07:00"
        )
        assertEquals(
            ScheduleAction.NONE,
            ConnectionScheduleEngine.evaluate(broken, 1, 12 * 60, connected = false)
        )
    }

    // ------------------------------------------------------------------
    // JSON round-trip + describe
    // ------------------------------------------------------------------

    @Test
    fun `schedule json round-trips`() {
        val original = ConnectionSchedule(
            enabled = true, connectAt = "23:15", disconnectAt = "06:45",
            daysMask = ConnectionSchedule.WEEKDAYS
        )
        val parsed = ConnectionSchedule.fromJson(original.toJson())
        assertEquals(original, parsed)
    }

    @Test
    fun `json garbage degrades to default schedule`() {
        val fallback = ConnectionSchedule.fromJson("not json at all")
        assertFalse(fallback.enabled)
    }

    @Test
    fun `describe summarizes window and days`() {
        assertEquals(
            "off",
            ConnectionScheduleEngine.describe(ConnectionSchedule(enabled = false))
        )
        assertEquals(
            "22:00-07:00 Mon-Sun",
            ConnectionScheduleEngine.describe(nightSchedule)
        )
        assertEquals(
            "09:00-18:00 Mon-Fri",
            ConnectionScheduleEngine.describe(
                daySchedule.copy(daysMask = ConnectionSchedule.WEEKDAYS)
            )
        )
    }
}
