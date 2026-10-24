package com.securenet.vpn.stats

import com.securenet.vpn.data.local.HistoryParser
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * v1.5 — Aggregates the encrypted session log's per-day usage and session
 * pairs into a monthly dashboard model. Pure Kotlin (java.time only), fully
 * JVM-testable; the UI layer only formats and draws.
 *
 * Day alignment: [HistoryParser.UsageDay.dayEpoch] is whole UTC days since
 * epoch (ms / 86 400 000). Everything here uses the same UTC epoch-day space
 * so chart bars line up exactly with what the log recorded.
 */
object MonthlyStatsEngine {

    data class DayUsage(
        val dayEpoch: Long,
        val rxBytes: Long,
        val txBytes: Long
    ) {
        val totalBytes: Long get() = rxBytes + txBytes
    }

    data class MonthStats(
        val year: Int,
        /** 1..12. */
        val month: Int,
        /** One bucket per calendar day of the month, ascending; zero-filled. */
        val days: List<DayUsage>,
        val totalRxBytes: Long,
        val totalTxBytes: Long,
        /** Days with any recorded traffic. */
        val activeDays: Int,
        /** Sessions whose start instant fell inside this month. */
        val sessions: Int,
        val connectedSeconds: Long,
        val peakDay: DayUsage?,
        val isCurrentMonth: Boolean,
        /** Index of today inside [days] for the live month, else -1. */
        val todayIndex: Int
    ) {
        val totalBytes: Long get() = totalRxBytes + totalTxBytes
        val avgDailyBytes: Long
            get() = if (activeDays <= 0) 0L else totalBytes / activeDays
    }

    /**
     * @param usage   raw per-day roll-ups from the log (may span any period)
     * @param sessions parsed session pairs (open sessions end at their start)
     * @param nowUtc  today in UTC — anchors "current month" and partial-day
     */
    fun compute(
        usage: List<HistoryParser.UsageDay>,
        sessions: List<HistoryParser.Session>,
        year: Int,
        month: Int,
        nowUtc: LocalDate = LocalDate.now(ZoneOffset.UTC)
    ): MonthStats {
        val ym = YearMonth.of(year, month)
        val firstEpoch = ym.atDay(1).toEpochDay()
        val lastEpoch = ym.atEndOfMonth().toEpochDay()

        // Defensive merge: duplicate day entries are summed (HistoryParser
        // already de-duplicates, but the engine must not silently drop data).
        val byDay = HashMap<Long, HistoryParser.UsageDay>()
        for (u in usage) {
            val prev = byDay[u.dayEpoch]
            byDay[u.dayEpoch] = if (prev == null) u else prev.copy(
                rxBytes = prev.rxBytes + u.rxBytes,
                txBytes = prev.txBytes + u.txBytes,
                connectedSeconds = prev.connectedSeconds + u.connectedSeconds
            )
        }
        val days = (firstEpoch..lastEpoch).map { epoch ->
            val u = byDay[epoch]
            DayUsage(
                dayEpoch = epoch,
                rxBytes = u?.rxBytes ?: 0L,
                txBytes = u?.txBytes ?: 0L
            )
        }

        val monthSessions = sessions.count { session ->
            val startDay = Instant.ofEpochMilli(session.startedAtMillis)
                .atZone(ZoneOffset.UTC).toLocalDate()
            startDay.year == year && startDay.monthValue == month
        }

        val totalRx = days.sumOf { it.rxBytes }
        val totalTx = days.sumOf { it.txBytes }
        val connectedSeconds = usage
            .filter { it.dayEpoch in firstEpoch..lastEpoch }
            .sumOf { it.connectedSeconds }

        val isCurrent = nowUtc.year == year && nowUtc.monthValue == month
        val todayIdx = if (isCurrent) {
            (nowUtc.toEpochDay() - firstEpoch).toInt().coerceIn(0, days.lastIndex)
        } else {
            -1
        }

        return MonthStats(
            year = year,
            month = month,
            days = days,
            totalRxBytes = totalRx,
            totalTxBytes = totalTx,
            activeDays = days.count { it.totalBytes > 0 },
            sessions = monthSessions,
            connectedSeconds = connectedSeconds,
            peakDay = days.maxByOrNull { it.totalBytes }?.takeIf { it.totalBytes > 0 },
            isCurrentMonth = isCurrent,
            todayIndex = todayIdx
        )
    }

    /** Shifts (year, month) by [offset] months; normalizes across year edges. */
    fun shift(year: Int, month: Int, offset: Int): Pair<Int, Int> {
        val total = year * 12 + (month - 1) + offset
        val y = Math.floorDiv(total, 12)
        val m = Math.floorMod(total, 12) + 1
        return y to m
    }

    /** "0 B", "512 B", "1.5 MB", "2.0 GB", "1.1 TB" — Locale.US for tests. */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < UNITS.lastIndex) {
            value /= 1024.0
            unit++
        }
        val formatted = if (value >= 100) String.format(java.util.Locale.US, "%.0f", value)
        else String.format(java.util.Locale.US, "%.1f", value)
        return "$formatted ${UNITS[unit]}"
    }

    /** "45s", "12m 30s", "3h 05m", "26h 40m" — compact dashboard style. */
    fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "0s"
        val s = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val m = (totalMinutes % 60).toInt()
        val h = totalMinutes / 60
        return when {
            h > 0 -> String.format(java.util.Locale.US, "%dh %02dm", h, m)
            m > 0 -> String.format(java.util.Locale.US, "%dm %02ds", m, s)
            else -> "${s}s"
        }
    }

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
}
