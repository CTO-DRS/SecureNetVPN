package com.securenet.vpn.util

/**
 * Minimal, dependency-free parser for the wg-quick INI dialect used by
 * WireGuard configuration files and QR imports:
 *
 *   [Interface]
 *   PrivateKey = ...
 *   Address = 10.66.66.2/32
 *   DNS = 1.1.1.1
 *
 *   [Peer]
 *   PublicKey = ...
 *   Endpoint = host:port
 *   AllowedIPs = 0.0.0.0/0, ::/0
 */
object WireGuardConfigParser {

    data class ParsedConfig(
        val interfacePrivateKey: String?,
        val interfaceAddresses: List<String>,
        val interfaceDnsServers: List<String>,
        val peerPublicKey: String?,
        val peerEndpoint: String?,
        val peerAllowedIps: List<String>,
        val persistentKeepalive: Int?
    )

    sealed class ParseResult {
        data class Success(val config: ParsedConfig) : ParseResult()
        data class Failure(val reason: String) : ParseResult()
    }

    fun parse(rawConfig: String): ParseResult {
        if (rawConfig.isBlank()) return ParseResult.Failure("Empty configuration")

        var section = ""
        var privateKey: String? = null
        var peerKey: String? = null
        var endpoint: String? = null
        var keepalive: Int? = null
        val addresses = mutableListOf<String>()
        val dnsServers = mutableListOf<String>()
        val allowedIps = mutableListOf<String>()

        rawConfig.lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().lowercase()
                if (section != SECTION_INTERFACE && section != SECTION_PEER) {
                    return ParseResult.Failure("Unknown section at line ${index + 1}: [$section]")
                }
                return@forEachIndexed
            }

            val separator = line.indexOf('=')
            if (separator <= 0) {
                return ParseResult.Failure("Malformed line ${index + 1}: $line")
            }
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()

            when (section) {
                SECTION_INTERFACE -> when (key) {
                    "privatekey" -> privateKey = value
                    "address" -> addresses += splitList(value)
                    "dns" -> if (!value.equals("0.0.0.0", true)) dnsServers += splitList(value)
                    "listenport", "mtu", "table", "postup", "postdown", "preup", "predown" -> {
                        // Accepted but ignored: managed by the app's own logic.
                    }
                    else -> return ParseResult.Failure("Unknown Interface key at line ${index + 1}: $key")
                }
                SECTION_PEER -> when (key) {
                    "publickey" -> peerKey = value
                    "endpoint" -> endpoint = value
                    "allowedips" -> allowedIps += splitList(value)
                    "persistentkeepalive" -> keepalive = value.toIntOrNull()
                        ?: return ParseResult.Failure("Invalid PersistentKeepalive at line ${index + 1}")
                    else -> return ParseResult.Failure("Unknown Peer key at line ${index + 1}: $key")
                }
            }
        }

        if (privateKey == null) return ParseResult.Failure("Missing [Interface] PrivateKey")
        if (peerKey == null) return ParseResult.Failure("Missing [Peer] PublicKey")

        return ParseResult.Success(
            ParsedConfig(
                interfacePrivateKey = privateKey,
                interfaceAddresses = addresses.toList(),
                interfaceDnsServers = dnsServers.toList(),
                peerPublicKey = peerKey,
                peerEndpoint = endpoint,
                peerAllowedIps = allowedIps.toList(),
                persistentKeepalive = keepalive
            )
        )
    }

    private fun splitList(value: String): List<String> =
        value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private const val SECTION_INTERFACE = "interface"
    private const val SECTION_PEER = "peer"
}
