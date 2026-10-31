package com.securenet.vpn.service

/**
 * v1.8.21 — pure text engine behind the richer persistent notification
 * (عرض أغنى في لوحة الإشعارات). Builds every string the status
 * notification renders — the compact session line, the expanded
 * download/upload body and the session-start clock — with zero Android
 * imports so the whole surface is JVM-testable ([NotifTextTest]).
 *
 * v1.8.23 adds the session-duration ladder ([formatDuration]), the
 * per-session average line ([averageLine] / [detailedBody]) and the
 * last-session summary shown when a tunnel drops ([lastSessionLine]).
 *
 * Byte formatting follows the binary convention the dashboard history
 * already uses (1024-based, one decimal from KB up, trailing ".0" trimmed);
 * the clock is derived from a caller-supplied UTC offset so it renders the
 * device's local time without ever touching java.util.TimeZone on the JVM.
 */
object NotifText {

    /** One decimal place from KB upward, binary units, negatives clamp to 0. */
    fun formatBytes(bytes: Long): String {
        val value = bytes.coerceAtLeast(0L)
        return when {
            value < 1024L -> "$value B"
            value < 1024L * 1024L ->
                trimOneDecimal(value / 1024.0) + " KB"
            value < 1024L * 1024L * 1024L ->
                trimOneDecimal(value / (1024.0 * 1024.0)) + " MB"
            else ->
                trimOneDecimal(value / (1024.0 * 1024.0 * 1024.0)) + " GB"
        }
    }

    /** Per-second rate — same ladder as [formatBytes] with a "/s" suffix. */
    fun formatSpeed(bytesPerSec: Long): String =
        formatBytes(bytesPerSec) + "/s"

    /**
     * The compact session line under the connected title:
     * "↓ 1.2 MB · ↑ 340 KB" (totals, not rates).
     */
    fun sessionLine(rxBytes: Long, txBytes: Long): String =
        "↓ ${formatBytes(rxBytes)} · ↑ ${formatBytes(txBytes)}"

    /**
     * The expanded body: one line per direction (total + live rate), then
     * the session-start clock — dropped when no session anchor exists yet.
     * [downLabel]/[upLabel]/[sinceLabel] are the caller's translated
     * strings; everything numeric comes from the pure formatters.
     */
    fun expandedBody(
        rxBytes: Long,
        txBytes: Long,
        rxSpeed: Long,
        txSpeed: Long,
        startedAtMillis: Long,
        utcOffsetMillis: Long,
        downLabel: String,
        upLabel: String,
        sinceLabel: String
    ): String {
        val down = "$downLabel ${formatBytes(rxBytes)} (${formatSpeed(rxSpeed)})"
        val up = "$upLabel ${formatBytes(txBytes)} (${formatSpeed(txSpeed)})"
        return if (startedAtMillis > 0L) {
            "$down\n$up\n$sinceLabel ${clock(startedAtMillis, utcOffsetMillis)}"
        } else {
            "$down\n$up"
        }
    }

    /**
     * Renders [epochMillis] as "HH:mm" in the zone the caller encodes via
     * [utcOffsetMillis] (usually TimeZone.getDefault().getOffset(now)).
     */
    fun clock(epochMillis: Long, utcOffsetMillis: Long): String {
        val local = epochMillis + utcOffsetMillis
        val minutesInDay = (local / 60_000L).mod(24L * 60L)
        val hour = minutesInDay / 60L
        val minute = minutesInDay % 60L
        return buildString {
            append(if (hour < 10L) "0" else "").append(hour)
            append(':')
            append(if (minute < 10L) "0" else "").append(minute)
        }
    }

    /**
     * v1.8.23 — human session duration: "45 s" under a minute, "42 min"
     * under an hour, then "1 h 5 min" (hours never pad, minutes never pad).
     * Negatives clamp to "0 s".
     */
    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        return when {
            totalSeconds < 60L -> "$totalSeconds s"
            totalSeconds < 3600L -> "${totalSeconds / 60L} min"
            else -> "${totalSeconds / 3600L} h ${(totalSeconds % 3600L) / 60L} min"
        }
    }

    /**
     * v1.8.23 — the per-session average line appended to the detailed
     * expanded body: "Average ↓ 12.3 KB/s · ↑ 3.4 KB/s". Null without a
     * session anchor or before one elapsed second (the live rates already
     * cover that moment).
     */
    fun averageLine(
        rxBytes: Long,
        txBytes: Long,
        startedAtMillis: Long,
        nowMillis: Long,
        avgLabel: String
    ): String? {
        if (startedAtMillis <= 0L) return null
        val seconds = ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)
        if (seconds <= 0L) return null
        return "$avgLabel ↓ ${formatSpeed(rxBytes / seconds)} · ↑ ${formatSpeed(txBytes / seconds)}"
    }

    /**
     * v1.8.23 — the DETAILED body: the v1.8.21 expanded body plus the
     * session-average line when a session anchor exists, so four lines
     * carry totals, live rates, the start clock and the session average.
     */
    fun detailedBody(
        rxBytes: Long,
        txBytes: Long,
        rxSpeed: Long,
        txSpeed: Long,
        startedAtMillis: Long,
        nowMillis: Long,
        utcOffsetMillis: Long,
        downLabel: String,
        upLabel: String,
        sinceLabel: String,
        avgLabel: String
    ): String {
        val base = expandedBody(
            rxBytes, txBytes, rxSpeed, txSpeed,
            startedAtMillis, utcOffsetMillis, downLabel, upLabel, sinceLabel
        )
        val avg = averageLine(rxBytes, txBytes, startedAtMillis, nowMillis, avgLabel)
        return if (avg == null) base else "$base\n$avg"
    }

    /**
     * v1.8.23 — the summary shown when a tunnel is down: what the last
     * session achieved — "Last session 42 min · ↓ 156 MB · ↑ 12 MB".
     */
    fun lastSessionLine(
        durationMillis: Long,
        rxBytes: Long,
        txBytes: Long,
        label: String
    ): String =
        "$label ${formatDuration(durationMillis)} · ${sessionLine(rxBytes, txBytes)}"

    /** One decimal (rounded, not truncated), trailing ".0" trimmed: "1.2 MB". */
    private fun trimOneDecimal(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", rounded)
        }
    }
}
