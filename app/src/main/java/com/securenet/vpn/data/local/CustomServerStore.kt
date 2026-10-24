package com.securenet.vpn.data.local

import org.json.JSONArray
import org.json.JSONObject

/**
 * v1.5 — Encrypted-prefs backed registry of user-imported WireGuard servers.
 *
 * An imported profile carries everything a stock [com.securenet.vpn.data.model.VpnServer]
 * has, plus the two secrets a wg-quick file ships with and a stock server
 * never does:
 *  - [privateKey]: the Interface PrivateKey (overrides the app's device key)
 *  - [presharedKey]: optional layer-2 PSK
 *
 * The whole registry is stored as one JSON string inside
 * [SecurePrefs.customServersJson] (EncryptedSharedPreferences) and every
 * operation here is pure — encode/decode on plain strings — so the logic is
 * fully unit-testable on the JVM.
 */
object CustomServerStore {

    /** One imported WireGuard profile. */
    data class Entry(
        val server: com.securenet.vpn.data.model.VpnServer,
        /** wg-quick Interface PrivateKey; null → use the app's device key. */
        val privateKey: String?,
        /** Optional PresharedKey from the [Peer] section. */
        val presharedKey: String?,
        /** Where the profile came from: "qr", "file" or "paste" (display only). */
        val source: String
    )

    fun load(raw: String): List<Entry> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val serverJson = o.optString("server")
                if (serverJson.isBlank()) return@mapNotNull null
                val server = try {
                    com.securenet.vpn.data.model.VpnServer.fromVpnServerJson(serverJson)
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                Entry(
                    server = server,
                    privateKey = o.optString("privateKey").takeIf { it.isNotEmpty() },
                    presharedKey = o.optString("presharedKey").takeIf { it.isNotEmpty() },
                    source = o.optString("source", "paste")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Appends or replaces (by server id) and returns the updated JSON blob. */
    fun upsert(raw: String, entry: Entry): String {
        val rest = load(raw).filterNot { it.server.id == entry.server.id }
        val arr = JSONArray()
        // Newest profile floats to the top of the server list.
        arr.put(encode(entry))
        rest.forEach { arr.put(encode(it)) }
        return arr.toString()
    }

    /** Removes by server id and returns the updated JSON blob. */
    fun remove(raw: String, serverId: String): String {
        val rest = load(raw).filterNot { it.server.id == serverId }
        val arr = JSONArray()
        rest.forEach { arr.put(encode(it)) }
        return arr.toString()
    }

    fun find(raw: String, serverId: String): Entry? =
        load(raw).firstOrNull { it.server.id == serverId }

    fun count(raw: String): Int = load(raw).size

    private fun encode(entry: Entry): JSONObject = JSONObject().apply {
        put("server", entry.server.toVpnServerJson())
        entry.privateKey?.let { put("privateKey", it) }
        entry.presharedKey?.let { put("presharedKey", it) }
        put("source", entry.source)
    }
}
