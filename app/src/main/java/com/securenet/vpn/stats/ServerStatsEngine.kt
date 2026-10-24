package com.securenet.vpn.stats

import com.securenet.vpn.data.local.HistoryParser

/**
 * v1.6 — Per-server aggregation over the parsed session timeline.
 *
 * Pure Kotlin, fully JVM-testable. Sessions come from [HistoryParser]
 * (open sessions count their start instant as both start and end, so a
 * live tunnel contributes 0 seconds until it closes — same convention the
 * monthly dashboard uses).
 *
 * Traffic bytes are logged per-day globally, not per-server, so connected
 * time, session counts, failures and last-used are the honest per-server
 * metrics here.
 */
object ServerStatsEngine {

    data class ServerUsage(
        val serverId: String,
        val sessions: Int,
        val connectedSeconds: Long,
        /** Newest activity seen for this server (session end instant). */
        val lastUsedMillis: Long,
        val failures: Int
    )

    /**
     * Aggregates sessions per server, sorted by connected time (desc),
     * then session count, then recency — "most used first" ordering.
     */
    fun compute(sessions: List<HistoryParser.Session>): List<ServerUsage> {
        if (sessions.isEmpty()) return emptyList()
        val byServer = LinkedHashMap<String, Acc>()
        for (s in sessions) {
            val id = s.serverId ?: continue
            val acc = byServer.getOrPut(id) { Acc() }
            acc.sessions++
            acc.seconds += s.durationMillis / 1000
            val end = maxOf(s.endedAtMillis, s.startedAtMillis)
            if (end > acc.last) acc.last = end
            if (s.endEvent == "failed") acc.failures++
        }
        return byServer.map { (id, acc) ->
            ServerUsage(
                serverId = id,
                sessions = acc.sessions,
                connectedSeconds = acc.seconds,
                lastUsedMillis = acc.last,
                failures = acc.failures
            )
        }.sortedWith(
            compareByDescending<ServerUsage> { it.connectedSeconds }
                .thenByDescending { it.sessions }
                .thenByDescending { it.lastUsedMillis }
        )
    }

    /**
     * Share (0..100) of this server's connected time within [usages].
     * Returns 0 when no server has recorded time at all.
     */
    fun shareSeconds(usage: ServerUsage, usages: List<ServerUsage>): Int {
        val total = usages.sumOf { it.connectedSeconds }
        if (total <= 0L) return 0
        return ((usage.connectedSeconds * 100.0) / total).toInt().coerceIn(0, 100)
    }

    /**
     * Friendly display name: a resolved name (repository list, imported
     * profiles) wins; otherwise the raw id is shown unchanged.
     */
    fun displayName(serverId: String, names: Map<String, String>): String {
        val resolved = names[serverId]
        return if (resolved.isNullOrBlank()) serverId else resolved
    }

    private class Acc {
        var sessions = 0
        var seconds = 0L
        var last = 0L
        var failures = 0
    }
}
