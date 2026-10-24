package com.securenet.vpn.intelligence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Measures live TCP handshake latency to a set of VPN endpoints in parallel.
 * Short timeouts keep the whole sweep under ~1s even when some endpoints are
 * unreachable. Results feed [ServerRecommender].
 */
class ServerLatencyProber(
    private val timeoutMs: Int = 900,
    private val attempts: Int = 2
) {

    /** Probe one endpoint; returns -1 when unreachable. */
    fun probe(host: String, port: Int): Int {
        val samples = mutableListOf<Long>()
        repeat(attempts) {
            val socket = Socket()
            try {
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                samples.add((System.nanoTime() - start) / 1_000_000)
            } catch (_: Exception) {
                // skip
            } finally {
                runCatching { socket.close() }
            }
        }
        if (samples.isEmpty()) return -1
        samples.sort()
        return samples.first().toInt()   // best-of-N: handshake floor
    }

    /** Probe many endpoints concurrently; unreachable hosts are omitted. */
    suspend fun probeAll(
        endpoints: List<Pair<String, Int>>,
        keys: List<String>
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        require(endpoints.size == keys.size) { "endpoints/keys size mismatch" }
        endpoints.mapIndexed { index, (host, port) ->
            async { keys[index] to probe(host, port) }
        }.awaitAll()
            .filter { it.second > 0 }
            .toMap()
    }
}
