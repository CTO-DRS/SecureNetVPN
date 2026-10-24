package com.securenet.vpn.data.remote

import com.securenet.vpn.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Bootstrap configuration transport. Certificate pinning is enforced through
 * OkHttp's [CertificatePinner]; pins arrive via remote config (never hard
 * coded) and are merged with the network-security-config floor.
 */
class RemoteConfigApi {

    private var pinnedClient: OkHttpClient = buildClient(emptyList())

    /** Builds/refreshes a client with HPKP-style pins from remote config. */
    @Synchronized
    fun updatePins(pins: List<String>) {
        if (pins.isEmpty()) return
        pinnedClient = buildClient(pins)
    }

    /**
     * Fetches the bootstrap JSON (server list, pins, flags).
     * Returns null on any failure so callers fall back to the cache.
     */
    @Synchronized
    fun fetchBootstrapJson(): String? = try {
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + BuildConfig.REMOTE_CONFIG_PATH)
            .header("Accept", "application/json")
            .header("User-Agent", "SecureNetVPN/1.0 (Android)")
            .build()

        pinnedClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (_: Exception) {
        // Network/pin failures degrade to the encrypted cache.
        null
    }

    private fun buildClient(pins: List<String>): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (pins.isNotEmpty()) {
            val pinner = CertificatePinner.Builder().apply {
                val host = java.net.URI(BuildConfig.API_BASE_URL).host ?: ""
                pins.forEach { pin ->
                    if (pin.isNotBlank()) add(host, pin.trim())
                }
            }.build()
            builder.certificatePinner(pinner)
        }
        return builder.build()
    }
}
