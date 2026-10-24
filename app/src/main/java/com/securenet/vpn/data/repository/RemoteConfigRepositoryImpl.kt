package com.securenet.vpn.data.repository

import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.data.remote.RemoteConfigApi
import com.securenet.vpn.domain.repository.RemoteConfigRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remote-config plumbing: fetch once per 12h (or forced), parse, encrypt-cache,
 * and feed fresh certificate pins into the OkHttp layer. All failures degrade
 * gracefully to the cached snapshot so the app is fully usable offline.
 */
class RemoteConfigRepositoryImpl(
    private val api: RemoteConfigApi,
    private val securePrefs: SecurePrefs
) : RemoteConfigRepository {

    @Volatile
    private var parsedServers: List<VpnServer> = emptyList()

    @Volatile
    private var parsedPins: List<String> = emptyList()

    init {
        hydrateFromCache()
    }

    override suspend fun fetch(forceRefresh: Boolean): Boolean {
        val age = System.currentTimeMillis() - securePrefs.remoteConfigFetchedAt
        if (!forceRefresh && age < REFRESH_INTERVAL_MS) return true

        val raw = api.fetchBootstrapJson() ?: return securePrefs.cachedRemoteConfig != null
        return runCatching { parseAndStore(raw) }.getOrDefault(false)
    }

    override fun cachedServers(): List<VpnServer> {
        if (parsedServers.isEmpty()) hydrateFromCache()
        return parsedServers
    }

    override fun cachedCertificatePins(): List<String> = parsedPins

    private fun parseAndStore(raw: String): Boolean {
        val root = JSONObject(raw)
        val servers = root.optJSONArray(KEY_SERVERS) ?: JSONArray()
        val list = mutableListOf<VpnServer>()
        for (i in 0 until servers.length()) {
            val o = servers.getJSONObject(i)
            val publicKey = o.optString("public_key", "")
            if (publicKey.isBlank()) continue
            list += VpnServer(
                id = o.optString("id", "srv-$i"),
                name = o.optString("name", "Unknown"),
                countryCode = o.optString("country_code", "??"),
                city = o.optString("city", ""),
                endpointHost = o.optString("endpoint_host", ""),
                endpointPort = o.optInt("endpoint_port", 51820),
                publicKey = publicKey,
                localAddressV4 = o.optString("local_address", "10.66.66.2"),
                dnsPrimary = o.optString("dns_primary", "10.66.66.1"),
                dnsSecondary = o.optString("dns_secondary", "1.1.1.1"),
                loadPercent = o.optInt("load_percent", 0),
                supportsIpv6 = o.optBoolean("ipv6", false)
            )
        }

        val pins = mutableListOf<String>()
        val pinArray = root.optJSONArray(KEY_PINS)
        pinArray?.let { arr ->
            for (i in 0 until arr.length()) pins += arr.optString(i, "")
        }

        // Hand the fresh pins to OkHttp before any next call.
        api.updatePins(pins.filter { it.isNotBlank() })

        parsedServers = list
        parsedPins = pins
        securePrefs.cachedRemoteConfig = raw
        securePrefs.remoteConfigFetchedAt = System.currentTimeMillis()
        return list.isNotEmpty() || pins.isNotEmpty()
    }

    private fun hydrateFromCache() {
        val raw = securePrefs.cachedRemoteConfig ?: return
        runCatching {
            val root = JSONObject(raw)
            val servers = root.optJSONArray(KEY_SERVERS) ?: JSONArray()
            parsedServers = (0 until servers.length()).mapNotNull { i ->
                val o = servers.getJSONObject(i)
                val key = o.optString("public_key", "")
                if (key.isBlank()) {
                    null
                } else {
                    VpnServer(
                        id = o.optString("id", "srv-$i"),
                        name = o.optString("name", "Unknown"),
                        countryCode = o.optString("country_code", "??"),
                        city = o.optString("city", ""),
                        endpointHost = o.optString("endpoint_host", ""),
                        endpointPort = o.optInt("endpoint_port", 51820),
                        publicKey = key,
                        localAddressV4 = o.optString("local_address", "10.66.66.2"),
                        dnsPrimary = o.optString("dns_primary", "10.66.66.1"),
                        dnsSecondary = o.optString("dns_secondary", "1.1.1.1"),
                        loadPercent = o.optInt("load_percent", 0),
                        supportsIpv6 = o.optBoolean("ipv6", false)
                    )
                }
            }
            parsedPins = root.optJSONArray(KEY_PINS)?.let { arr ->
                (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
            } ?: emptyList()
        }
    }

    private companion object {
        const val KEY_SERVERS = "servers"
        const val KEY_PINS = "certificate_pins"
        const val REFRESH_INTERVAL_MS = 12L * 60 * 60 * 1000
    }
}
