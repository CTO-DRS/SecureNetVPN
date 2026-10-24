package com.securenet.vpn.intelligence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * On-device connection quality intelligence.
 *
 * Measures:
 *  - ping:   3x TCP handshake time to the target host:port, median kept
 *  - down:   streamed HTTP GET of a fixed payload, throughput = bytes / ms
 *  - up:     HTTP POST of a fixed random payload, throughput = bytes / ms
 *
 * Endpoints default to Cloudflare's public speed endpoints (no account, no
 * tracking cookies); inject a different base URL for self-hosted probes.
 * The engine is engine-only: no Android imports -> JVM unit-testable with a
 * fake OkHttpClient.
 */
class SpeedTestEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val downloadUrl: String = DEFAULT_DOWNLOAD_URL,
    private val uploadUrl: String = DEFAULT_UPLOAD_URL,
    private val downloadBytes: Int = 6_000_000,
    private val uploadBytes: Int = 2_000_000
) {

    data class Result(
        val pingMs: Int,
        val downloadMbps: Double,
        val uploadMbps: Double,
        val completedAtMillis: Long
    ) {
        fun toJson(): String = JSONObject().apply {
            put("ping", pingMs)
            put("down", downloadMbps)
            put("up", uploadMbps)
            put("at", completedAtMillis)
        }.toString()

        companion object {
            fun fromJson(raw: String): Result? = runCatching {
                val o = JSONObject(raw)
                Result(
                    pingMs = o.getInt("ping"),
                    downloadMbps = o.getDouble("down"),
                    uploadMbps = o.getDouble("up"),
                    completedAtMillis = o.getLong("at")
                )
            }.getOrNull()
        }
    }

    /** Ring buffer of the last N results, JSON-serializable for SecurePrefs. */
    data class History(val results: List<Result>) {
        fun toJson(): String {
            val arr = JSONArray()
            results.forEach { arr.put(JSONObject(it.toJson())) }
            return arr.toString()
        }

        companion object {
            const val MAX_ENTRIES = 10

            fun fromJson(raw: String): History = runCatching {
                val arr = JSONArray(raw)
                History((0 until arr.length())
                    .mapNotNull { Result.fromJson(arr.getJSONObject(it).toString()) }
                    .sortedByDescending { it.completedAtMillis }
                    .take(MAX_ENTRIES))
            }.getOrDefault(History(emptyList()))

            /** Pure helper: append + trim, returns a NEW History. */
            fun appended(history: History, result: Result): History =
                History((listOf(result) + history.results).take(MAX_ENTRIES))
        }
    }

    suspend fun run(host: String, port: Int): Result = withContext(Dispatchers.IO) {
        val ping = measurePing(host, port)
        val down = measureDownload()
        val up = measureUpload()
        Result(
            pingMs = ping,
            downloadMbps = down,
            uploadMbps = up,
            completedAtMillis = System.currentTimeMillis()
        )
    }

    /** Median of 3 TCP-connect probes; failures fall back to remaining probes. */
    fun measurePing(host: String, port: Int, attempts: Int = 3): Int {
        val samples = mutableListOf<Long>()
        repeat(attempts) {
            val socket = Socket()
            try {
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), PING_TIMEOUT_MS)
                samples.add((System.nanoTime() - start) / 1_000_000)
            } catch (_: Exception) {
                // unreachable endpoint: skip sample
            } finally {
                runCatching { socket.close() }
            }
        }
        if (samples.isEmpty()) return -1
        samples.sort()
        return samples[samples.size / 2].toInt()
    }

    private fun measureDownload(): Double = runCatching {
        val request = Request.Builder()
            .url("$downloadUrl?bytes=$downloadBytes")
            .header("Cache-Control", "no-store")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching 0.0
            val source = response.body?.source() ?: return@runCatching 0.0
            val start = System.nanoTime()
            var total = 0L
            val buffer = ByteArray(64 * 1024)
            val input = response.body!!.byteStream()
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total >= downloadBytes) break
            }
            val millis = (System.nanoTime() - start) / 1_000_000.0
            if (millis <= 0) 0.0 else (total * 8.0) / (millis / 1000.0) / 1_000_000.0
        }
    }.getOrDefault(0.0)

    private fun measureUpload(): Double = runCatching {
        val payload = ByteArray(uploadBytes)
            .also { java.util.Random(42).nextBytes(it) }
            .toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(uploadUrl)
            .post(payload)
            .build()
        val start = System.nanoTime()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching 0.0
            val millis = (System.nanoTime() - start) / 1_000_000.0
            if (millis <= 0) 0.0 else (uploadBytes * 8.0) / (millis / 1000.0) / 1_000_000.0
        }
    }.getOrDefault(0.0)

    companion object {
        const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down"
        const val DEFAULT_UPLOAD_URL = "https://speed.cloudflare.com/__up"
        private const val PING_TIMEOUT_MS = 1_500
    }
}
