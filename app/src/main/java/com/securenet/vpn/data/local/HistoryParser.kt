package com.securenet.vpn.data.local

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * v1.2 — Turns the encrypted JSONL session log produced by
 * [ConnectionLogger] into a user-friendly timeline: VPN sessions
 * (connected → disconnected/failed pairs) and daily usage roll-ups.
 *
 * Parsing is hand-rolled (no org.json) so it stays unit-testable on the JVM.
 */
class HistoryParser {

    data class Event(
        val tsMillis: Long,
        val name: String,
        val serverId: String?,
        val detail: String?
    )

    data class Session(
        val serverId: String?,
        val startedAtMillis: Long,
        val endedAtMillis: Long,
        /** "disconnected", "failed", or "" while the session is still open. */
        val endEvent: String,
        val endDetail: String?
    ) {
        val durationMillis: Long get() = (endedAtMillis - startedAtMillis).coerceAtLeast(0)
        val isOpen: Boolean get() = endEvent.isEmpty()
    }

    data class UsageDay(
        val dayEpoch: Long,
        val rxBytes: Long,
        val txBytes: Long,
        val connectedSeconds: Long
    )

    data class Timeline(
        val sessions: List<Session>,
        val usage: List<UsageDay>,
        val totalEvents: Int
    )

    fun parse(lines: List<String>): Timeline {
        val events = mutableListOf<Event>()
        val usageByDay = LinkedHashMap<Long, UsageDay>()

        for (line in lines) {
            val l = line.trim()
            if (l.isEmpty() || !l.startsWith("{")) continue
            val name = field(l, "event") ?: continue
            val ts = field(l, "ts")?.let { parseTs(it) } ?: continue

            if (name == "usage") {
                val day = field(l, "day")?.toLongOrNull() ?: continue
                val rx = field(l, "rx")?.toLongOrNull() ?: 0L
                val tx = field(l, "tx")?.toLongOrNull() ?: 0L
                val secs = field(l, "connected_s")?.toLongOrNull() ?: 0L
                val prev = usageByDay[day] ?: UsageDay(day, 0, 0, 0)
                usageByDay[day] = prev.copy(
                    rxBytes = prev.rxBytes + rx,
                    txBytes = prev.txBytes + tx,
                    connectedSeconds = prev.connectedSeconds + secs
                )
                continue
            }

            events += Event(
                tsMillis = ts,
                name = name,
                serverId = field(l, "server"),
                detail = field(l, "detail")
            )
        }

        // Pair connected→(disconnected|failed) into sessions.
        val sessions = mutableListOf<Session>()
        var openStart: Event? = null
        for (e in events) {
            when (e.name) {
                "connected" -> {
                    openStart?.let {
                        sessions += Session(it.serverId, it.tsMillis, it.tsMillis, "failed", null)
                    }
                    openStart = e
                }
                "disconnected", "failed" -> {
                    openStart?.let {
                        sessions += Session(it.serverId, it.tsMillis, e.tsMillis, e.name, e.detail)
                        openStart = null
                    }
                }
            }
        }
        openStart?.let { sessions += Session(it.serverId, it.tsMillis, it.tsMillis, "", null) }

        return Timeline(
            sessions = sessions.sortedByDescending { it.startedAtMillis },
            usage = usageByDay.values.sortedByDescending { it.dayEpoch },
            totalEvents = events.size
        )
    }

    private fun parseTs(raw: String): Long = try {
        synchronized(TIMESTAMP_FORMAT) {
            TIMESTAMP_FORMAT.parse(raw)?.time ?: 0L
        }
    } catch (_: Exception) {
        0L
    }

    /** Extracts a JSON string field with minimal unescaping (our writer's format). */
    private fun field(json: String, key: String): String? {
        val needle = "\"$key\":"
        val start = json.indexOf(needle).takeIf { it >= 0 } ?: return null
        val valueStart = start + needle.length
        if (valueStart >= json.length) return null
        if (json[valueStart] != '"') {
            // Numeric / boolean field: read until , or }.
            val endIdx = json.indexOfAny(charArrayOf(',', '}'), valueStart)
            if (endIdx < 0) return null
            return json.substring(valueStart, endIdx).trim()
        }
        val sb = StringBuilder()
        var i = valueStart + 1
        while (i < json.length) {
            val c = json[i]
            when {
                c == '"' -> return sb.toString()
                c == '\\' && i + 1 < json.length -> {
                    when (val next = json[i + 1]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        else -> sb.append(next)
                    }
                    i++
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    companion object {
        // Same pattern ConnectionLogger writes with.
        private val TIMESTAMP_FORMAT =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    }
}
