package com.securenet.vpn

import com.securenet.vpn.data.local.HistoryParser
import com.securenet.vpn.stats.MonthlyStatsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * v1.5 — Monthly dashboard engine: bucketing, aggregation, formatting.
 */
class MonthlyStatsEngineTest {

    private val nowUtc: LocalDate = LocalDate.of(2026, 10, 15)

    private fun usage(dayEpoch: Long, rx: Long, tx: Long, secs: Long = 0) =
        HistoryParser.UsageDay(dayEpoch, rx, tx, secs)

    private fun epochDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).toEpochDay()

    @Test
    fun `fills every calendar day of the month ascending`() {
        val stats = MonthlyStatsEngine.compute(
            usage = listOf(usage(epochDay(2026, 10, 5), 100, 50)),
            sessions = emptyList(),
            year = 2026, month = 10, nowUtc = nowUtc
        )
        assertEquals(31, stats.days.size)
        assertEquals(epochDay(2026, 10, 1), stats.days.first().dayEpoch)
        assertEquals(epochDay(2026, 10, 31), stats.days.last().dayEpoch)
        assertEquals(100L, stats.days[4].rxBytes) // day 5 → index 4
        assertEquals(0L, stats.days[0].rxBytes)
    }

    @Test
    fun `totals active days and peak are aggregated`() {
        val stats = MonthlyStatsEngine.compute(
            usage = listOf(
                usage(epochDay(2026, 10, 3), 1_000, 500),
                usage(epochDay(2026, 10, 3), 200, 100),   // same day → summed
                usage(epochDay(2026, 10, 20), 5_000, 2_500),
                usage(epochDay(2026, 9, 7), 99_000, 99_000) // other month → ignored
            ),
            sessions = emptyList(),
            year = 2026, month = 10, nowUtc = nowUtc
        )
        assertEquals(6_200L, stats.totalRxBytes) // 1000 + 200 + 5000
        assertEquals(3_100L, stats.totalTxBytes)
        assertEquals(9_300L, stats.totalBytes)
        assertEquals(2, stats.activeDays)
        assertEquals(epochDay(2026, 10, 20), stats.peakDay?.dayEpoch)
        assertEquals(5_000L, stats.peakDay?.rxBytes)
    }

    @Test
    fun `empty month has zero stats and no peak`() {
        val stats = MonthlyStatsEngine.compute(
            usage = emptyList(),
            sessions = emptyList(),
            year = 2026, month = 2, nowUtc = nowUtc
        )
        assertEquals(28, stats.days.size)
        assertEquals(0L, stats.totalBytes)
        assertEquals(0, stats.activeDays)
        assertNull(stats.peakDay)
        assertEquals(0L, stats.avgDailyBytes)
    }

    @Test
    fun `sessions counted by their start day inside the month`() {
        fun sessionAt(year: Int, month: Int, day: Int): HistoryParser.Session {
            val start = LocalDate.of(year, month, day).atStartOfDay()
                .toInstant(ZoneOffset.UTC).toEpochMilli()
            return HistoryParser.Session("srv", start, start + 60_000, "disconnected", null)
        }
        val stats = MonthlyStatsEngine.compute(
            usage = emptyList(),
            sessions = listOf(
                sessionAt(2026, 10, 2),
                sessionAt(2026, 10, 30),
                sessionAt(2026, 11, 1),
                sessionAt(2026, 9, 30)
            ),
            year = 2026, month = 10, nowUtc = nowUtc
        )
        assertEquals(2, stats.sessions)
    }

    @Test
    fun `connected seconds only from the requested month`() {
        val stats = MonthlyStatsEngine.compute(
            usage = listOf(
                usage(epochDay(2026, 10, 3), 10, 10, secs = 3600),
                usage(epochDay(2026, 10, 4), 10, 10, secs = 60),
                usage(epochDay(2026, 8, 4), 10, 10, secs = 9999)
            ),
            sessions = emptyList(),
            year = 2026, month = 10, nowUtc = nowUtc
        )
        assertEquals(3660L, stats.connectedSeconds)
    }

    @Test
    fun `current month flags today index`() {
        val stats = MonthlyStatsEngine.compute(
            usage = emptyList(),
            sessions = emptyList(),
            year = 2026, month = 10, nowUtc = nowUtc
        )
        assertTrue(stats.isCurrentMonth)
        assertEquals(14, stats.todayIndex) // Oct 15 → index 14

        val past = MonthlyStatsEngine.compute(
            usage = emptyList(),
            sessions = emptyList(),
            year = 2026, month = 9, nowUtc = nowUtc
        )
        assertFalse(past.isCurrentMonth)
        assertEquals(-1, past.todayIndex)
    }

    @Test
    fun `shift walks across year boundaries both directions`() {
        assertEquals(2026 to 1, MonthlyStatsEngine.shift(2025, 12, 1))
        assertEquals(2025 to 12, MonthlyStatsEngine.shift(2026, 1, -1))
        assertEquals(2026 to 10, MonthlyStatsEngine.shift(2026, 10, 0))
        assertEquals(2024 to 2, MonthlyStatsEngine.shift(2026, 2, -24))
    }

    @Test
    fun `byte formatting ladder`() {
        assertEquals("0 B", MonthlyStatsEngine.formatBytes(0))
        assertEquals("512 B", MonthlyStatsEngine.formatBytes(512))
        assertEquals("1.0 KB", MonthlyStatsEngine.formatBytes(1024))
        assertEquals("1.5 KB", MonthlyStatsEngine.formatBytes((1024 * 1.5).toLong()))
        assertEquals("1.0 MB", MonthlyStatsEngine.formatBytes(1024L * 1024))
        assertEquals("2.0 GB", MonthlyStatsEngine.formatBytes(2L * 1024 * 1024 * 1024))
        assertEquals("1.0 TB", MonthlyStatsEngine.formatBytes(1024L * 1024 * 1024 * 1024))
        assertEquals("150 GB", MonthlyStatsEngine.formatBytes(150L * 1024 * 1024 * 1024))
        assertEquals("0 B", MonthlyStatsEngine.formatBytes(-5))
    }

    @Test
    fun `duration formatting ladder`() {
        assertEquals("0s", MonthlyStatsEngine.formatDuration(0))
        assertEquals("45s", MonthlyStatsEngine.formatDuration(45))
        assertEquals("5m 30s", MonthlyStatsEngine.formatDuration(330))
        assertEquals("2h 05m", MonthlyStatsEngine.formatDuration(2 * 3600 + 5 * 60))
        assertEquals("26h 40m", MonthlyStatsEngine.formatDuration(26 * 3600 + 40 * 60))
    }

    @Test
    fun `average daily bytes divides by active days only`() {
        val stats = MonthlyStatsEngine.compute(
            usage = listOf(
                usage(epochDay(2026, 10, 1), 1_000, 1_000),
                usage(epochDay(2026, 10, 2), 2_000, 2_000)
            ),
            sessions = emptyList(),
            year = 2026, month = 10, nowUtc = nowUtc
        )
        assertEquals(2, stats.activeDays)
        assertEquals(3_000L, stats.avgDailyBytes)
    }
}
