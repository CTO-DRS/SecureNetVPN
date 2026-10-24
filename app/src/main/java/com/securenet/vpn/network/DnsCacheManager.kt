package com.securenet.vpn.network

import com.securenet.vpn.data.local.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * Resolving layer on top of [DnsCache]: TTL-aware lookups on Dispatchers.IO
 * plus a periodic pre-warm of the server endpoints so tunnel setup never
 * waits on cold DNS. Statistics are surfaced on the dashboard.
 */
class DnsCacheManager(
    private val scope: CoroutineScope,
    private val securePrefs: SecurePrefs,
    private val cache: DnsCache = DnsCache()
) {

    suspend fun resolve(host: String): List<String> = withContext(Dispatchers.IO) {
        if (!securePrefs.dnsCacheEnabled) {
            return@withContext runCatching {
                InetAddress.getAllByName(host).map { it.hostAddress ?: "" }
            }.getOrDefault(emptyList())
        }

        cache.get(host)?.let { return@withContext it }

        val resolved = runCatching {
            InetAddress.getAllByName(host).map { it.hostAddress ?: "" }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

        if (resolved.isNotEmpty()) {
            cache.put(host, resolved)
        }
        resolved
    }

    /** Prewarms hot endpoints (selected server, API host) in the background. */
    fun prewarm(hosts: List<String>) {
        if (!securePrefs.dnsCacheEnabled) return
        scope.launch(Dispatchers.IO) {
            hosts.forEach { host ->
                if (cache.get(host) == null) {
                    runCatching {
                        val ips = InetAddress.getAllByName(host).map { it.hostAddress ?: "" }
                        if (ips.isNotEmpty()) cache.put(host, ips)
                    }
                }
            }
        }
    }

    fun statsSnapshot(): String = buildString {
        val total = cache.stats.hits + cache.stats.misses
        append("${cache.size()} entries · ")
        if (total > 0) {
            append("${(cache.hitRate() * 100).toInt()}% hits")
        } else {
            append("cold")
        }
    }

    fun clear() = cache.clear()
}
