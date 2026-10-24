package com.securenet.vpn.network

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Pure, testable TTL + LRU DNS cache. No Android dependencies so the whole
 * eviction/expiry logic is unit-testable on the JVM.
 */
class DnsCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val defaultTtlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    data class Entry(val ips: List<String>, val expiresAtMs: Long, var lastHitAtMs: Long)

    data class Stats(var hits: Long = 0, var misses: Long = 0, var expirations: Long = 0, var evictions: Long = 0)

    private val lock = ReentrantReadWriteLock()
    private val map = LinkedHashMap<String, Entry>(16, 0.75f, true) // access order
    val stats = Stats()

    fun get(host: String): List<String>? {
        val now = clock()
        lock.read {
            val entry = map[host.lowercase()]
            if (entry == null) {
                stats.misses++
                return null
            }
            if (entry.expiresAtMs <= now) {
                // An expired lookup is operationally a miss as well: the
                // resolver has to go back to the network for this host.
                stats.expirations++
                stats.misses++
                return null
            }
            entry.lastHitAtMs = now
            stats.hits++
            return entry.ips
        }
    }

    fun put(host: String, ips: List<String>, ttlMs: Long = defaultTtlMs) {
        if (ips.isEmpty() || ttlMs <= 0) return
        val key = host.lowercase()
        val now = clock()
        lock.write {
            // Evict LRU until there is room.
            while (map.size >= maxEntries) {
                val eldest = map.keys.firstOrNull() ?: break
                map.remove(eldest)
                stats.evictions++
            }
            map[key] = Entry(ips.toList(), now + ttlMs, now)
        }
    }

    fun invalidate(host: String) {
        lock.write { map.remove(host.lowercase()) }
    }

    fun clear() {
        lock.write { map.clear() }
    }

    fun size(): Int = lock.read { map.size }

    /** Hit rate in 0..1, or 0 when nothing has been resolved yet. */
    fun hitRate(): Double {
        val total = stats.hits + stats.misses
        return if (total == 0L) 0.0 else stats.hits.toDouble() / total
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 512
        const val DEFAULT_TTL_MS = 5L * 60 * 1000
        const val MIN_TTL_MS = 30_000L
        const val MAX_TTL_MS = 24L * 60 * 60 * 1000
    }
}
