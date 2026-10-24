package com.securenet.vpn.schedule

import org.json.JSONObject

/**
 * v1.3: Connection scheduling — a user-defined window in which the tunnel
 * should be up (e.g. 22:00 → 07:00 on weekdays). Pure data + engine, no
 * Android imports, so the whole decision matrix is unit-testable.
 */
data class ConnectionSchedule(
    val enabled: Boolean = false,
    /** "HH:mm" 24h — moment the tunnel should come up. */
    val connectAt: String = "22:00",
    /** "HH:mm" 24h — moment the tunnel should go down. */
    val disconnectAt: String = "07:00",
    /** Bitmask of active days: bit0 = Monday … bit6 = Sunday (ISO order). */
    val daysMask: Int = ALL_DAYS
) {

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled)
        put("connectAt", connectAt)
        put("disconnectAt", disconnectAt)
        put("daysMask", daysMask)
    }.toString()

    companion object {
        /** bit0 = Monday … bit6 = Sunday. */
        const val ALL_DAYS = 0b1111111
        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000

        fun fromJson(raw: String?): ConnectionSchedule = runCatching {
            val o = JSONObject(raw ?: return default())
            ConnectionSchedule(
                enabled = o.optBoolean("enabled", false),
                connectAt = o.optString("connectAt", "22:00"),
                disconnectAt = o.optString("disconnectAt", "07:00"),
                daysMask = o.optInt("daysMask", ALL_DAYS)
            )
        }.getOrDefault(default())

        fun default() = ConnectionSchedule()
    }
}

enum class ScheduleAction { NONE, CONNECT, DISCONNECT }

object ConnectionScheduleEngine {

    /**
     * Parses "HH:mm" (24h) into minutes-of-day. Returns null for anything
     * that is not a valid clock time — a malformed pref must never crash
     * the worker, it must simply disable scheduling.
     */
    fun toMinutes(hhmm: String): Int? {
        val parts = hhmm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun isValid(hhmm: String): Boolean = toMinutes(hhmm) != null

    /**
     * The core decision. ISO day (1 = Monday … 7 = Sunday), minutesOfDay
     * 0..1439, current tunnel state drives CONNECT / DISCONNECT / NONE.
     *
     * Window semantics: [connectAt, disconnectAt). A window that ends on or
     * after its start (e.g. 09:00 → 18:00) is a same-day window; one that
     * wraps (22:00 → 07:00) spans midnight and is active from connect time
     * until the end of the day AND from midnight until disconnect time.
     */
    fun evaluate(
        schedule: ConnectionSchedule,
        dayOfWeekIso: Int,
        minutesOfDay: Int,
        connected: Boolean
    ): ScheduleAction {
        if (!schedule.enabled) return ScheduleAction.NONE
        val connect = toMinutes(schedule.connectAt) ?: return ScheduleAction.NONE
        val disconnect = toMinutes(schedule.disconnectAt) ?: return ScheduleAction.NONE
        if (dayOfWeekIso !in 1..7) return ScheduleAction.NONE
        if (minutesOfDay !in 0..1439) return ScheduleAction.NONE

        val dayBit = 1 shl (dayOfWeekIso - 1)
        val activeToday = (schedule.daysMask and dayBit) != 0

        val insideWindow: Boolean = if (connect == disconnect) {
            // Degenerate window — treat the full day as active on scheduled days.
            activeToday
        } else if (connect < disconnect) {
            activeToday && minutesOfDay >= connect && minutesOfDay < disconnect
        } else {
            // Wraps midnight: part 1 belongs to today, part 2 to yesterday's bit.
            (activeToday && minutesOfDay >= connect) ||
                (dayBeforeActive(schedule.daysMask, dayOfWeekIso) && minutesOfDay < disconnect)
        }

        return when {
            insideWindow && !connected -> ScheduleAction.CONNECT
            !insideWindow && connected -> ScheduleAction.DISCONNECT
            else -> ScheduleAction.NONE
        }
    }

    /** For a midnight-wrapping window the tail (00:00→disconnect) inherits the previous day's mask bit. */
    private fun dayBeforeActive(daysMask: Int, dayOfWeekIso: Int): Boolean {
        val prev = if (dayOfWeekIso == 1) 7 else dayOfWeekIso - 1
        return (daysMask and (1 shl (prev - 1))) != 0
    }

    /** Machine-readable one-line summary for settings + dialog previews. */
    fun describe(schedule: ConnectionSchedule): String {
        if (!schedule.enabled) return "off"
        val days = when (schedule.daysMask) {
            ConnectionSchedule.ALL_DAYS -> "Mon-Sun"
            ConnectionSchedule.WEEKDAYS -> "Mon-Fri"
            ConnectionSchedule.WEEKEND -> "Sat-Sun"
            else -> buildString {
                var first = true
                for (d in 1..7) {
                    if (schedule.daysMask and (1 shl (d - 1)) != 0) {
                        if (!first) append(",")
                        append(DAY_ABBR[d])
                        first = false
                    }
                }
                if (isEmpty()) return "off"
            }
        }
        return "${schedule.connectAt}-${schedule.disconnectAt} $days"
    }

    val DAY_ABBR = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
}
