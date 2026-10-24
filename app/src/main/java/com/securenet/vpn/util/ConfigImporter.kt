package com.securenet.vpn.util

import com.securenet.vpn.data.local.CustomServerStore
import java.util.UUID

/**
 * v1.5 — Turns raw wg-quick text (QR payload, imported .conf file or pasted
 * text) into a storable [CustomServerStore.Entry]. Pure and JVM-testable.
 *
 * Validation ladder (a profile must clear all of it):
 *  1. parse via [WireGuardConfigParser] (structure, sections, unknown keys)
 *  2. PrivateKey / PublicKey / PresharedKey must be valid base64 WireGuard keys
 *  3. Endpoint must be present and shaped `host:port` (port 1..65535)
 *  4. Addresses / DNS / AllowedIPs degrade to safe full-tunnel defaults
 */
object ConfigImporter {

    sealed class Outcome {
        /** Ready-to-store profile + suggested display name. */
        data class Success(val entry: CustomServerStore.Entry) : Outcome()

        /** User-facing error code; see strings `import_error_*`. */
        data class Error(val reason: Reason, val detail: String? = null) : Outcome()
    }

    enum class Reason {
        EMPTY,
        PARSE_FAILED,
        BAD_PRIVATE_KEY,
        BAD_PEER_KEY,
        BAD_PRESHARED_KEY,
        MISSING_ENDPOINT,
        BAD_ENDPOINT
    }

    data class ImportSpec(
        val rawConfig: String,
        val displayName: String?,
        val source: String
    )

    fun import(spec: ImportSpec): Outcome {
        val text = spec.rawConfig.trim()
        if (text.isEmpty()) return Outcome.Error(Reason.EMPTY)

        val parsed = when (val r = WireGuardConfigParser.parse(text)) {
            is WireGuardConfigParser.ParseResult.Failure ->
                return Outcome.Error(Reason.PARSE_FAILED, r.reason)
            is WireGuardConfigParser.ParseResult.Success -> r.config
        }

        val privateKey = parsed.interfacePrivateKey.orEmpty().trim()
        if (!KeyUtils.isValidBase64Key(privateKey)) {
            return Outcome.Error(Reason.BAD_PRIVATE_KEY)
        }
        val peerKey = parsed.peerPublicKey.orEmpty().trim()
        if (!KeyUtils.isValidBase64Key(peerKey)) {
            return Outcome.Error(Reason.BAD_PEER_KEY)
        }
        val psk = parsed.peerPresharedKey?.trim()?.takeIf { it.isNotEmpty() }
        if (psk != null && !KeyUtils.isValidBase64Key(psk)) {
            return Outcome.Error(Reason.BAD_PRESHARED_KEY)
        }

        val endpoint = parsed.peerEndpoint?.trim().orEmpty()
        if (endpoint.isEmpty()) return Outcome.Error(Reason.MISSING_ENDPOINT)
        val (host, port) = splitEndpoint(endpoint)
            ?: return Outcome.Error(Reason.BAD_ENDPOINT, endpoint)

        val addresses = parsed.interfaceAddresses.filter { it.isNotBlank() }
        val v4Address = addresses.firstOrNull { it.contains('.') && !it.contains(':') }
            ?: "10.66.66.2/32"
        val localAddress = v4Address.substringBefore('/')
        val localPrefix = v4Address.substringAfter('/', "32").toIntOrNull()?.coerceIn(0, 32) ?: 32

        val dns = parsed.interfaceDnsServers.filter { it.isNotBlank() }
        val allowed = parsed.peerAllowedIps.filter { it.isNotBlank() }
            .ifEmpty { listOf(KeyUtils.ROUTE_ALL_V4) }
        val supportsIpv6 = allowed.any { it.contains(':') } || addresses.any { it.contains(':') }

        val label = spec.displayName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: host.substringBefore('.')
        val id = "custom_" + UUID.randomUUID().toString().substring(0, 8)

        val server = com.securenet.vpn.data.model.VpnServer(
            id = id,
            name = label,
            countryCode = "WG",
            city = label,
            endpointHost = host,
            endpointPort = port,
            publicKey = peerKey,
            localAddressV4 = localAddress,
            localPrefixV4 = localPrefix,
            dnsPrimary = dns.firstOrNull() ?: "1.1.1.1",
            dnsSecondary = dns.getOrNull(1) ?: "1.1.1.1",
            latencyMs = -1,
            loadPercent = 0,
            supportsIpv6 = supportsIpv6
        )

        return Outcome.Success(
            CustomServerStore.Entry(
                server = server,
                privateKey = privateKey,
                presharedKey = psk,
                source = spec.source
            )
        )
    }

    /** `host:port` → (host, port) or null. IPv6 endpoints must be [bracketed]. */
    fun splitEndpoint(endpoint: String): Pair<String, Int>? {
        val trimmed = endpoint.trim()
        // Bracketed IPv6: [2001:db8::1]:51820
        if (trimmed.startsWith("[")) {
            val close = trimmed.indexOf(']')
            if (close <= 1 || !trimmed.substring(close + 1).startsWith(":")) return null
            val host = trimmed.substring(1, close)
            val port = trimmed.substring(close + 2).toIntOrNull() ?: return null
            if (host.isBlank() || port !in 1..65535) return null
            return host to port
        }
        val lastColon = trimmed.lastIndexOf(':')
        if (lastColon <= 0 || lastColon == trimmed.length - 1) return null
        val host = trimmed.substring(0, lastColon)
        val port = trimmed.substring(lastColon + 1).toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        if (host.count { it == ':' } > 1) return null // bare (unbracketed) IPv6 → reject
        return host to port
    }

    /**
     * Human-readable one-line summary of a parsed profile for the preview
     * dialog (locale-neutral tokens; the UI adds localized labels).
     */
    fun summarize(entry: CustomServerStore.Entry): List<Pair<String, String>> = listOf(
        "name" to entry.server.name,
        "endpoint" to "${entry.server.endpointHost}:${entry.server.endpointPort}",
        "address" to "${entry.server.localAddressV4}/${entry.server.localPrefixV4}",
        "dns" to "${entry.server.dnsPrimary}, ${entry.server.dnsSecondary}",
        "psk" to if (entry.presharedKey != null) "yes" else "no"
    )
}
