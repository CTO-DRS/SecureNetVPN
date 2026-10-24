package com.securenet.vpn.util

import com.securenet.vpn.data.model.VpnServer

/**
 * v1.5 — Serializes the app's tunnel identity (server + secrets) back into
 * the wg-quick dialect, ready to be shown as a QR code or saved as a .conf
 * file. Output round-trips through [WireGuardConfigParser].
 */
object WgConfigBuilder {

    fun buildConf(
        server: VpnServer,
        privateKeyBase64: String,
        presharedKeyBase64: String? = null
    ): String {
        val config = WireGuardConfigParser.ParsedConfig(
            interfacePrivateKey = privateKeyBase64,
            interfaceAddresses = listOf(
                "${server.localAddressV4}/${server.localPrefixV4}"
            ),
            interfaceDnsServers = listOf(server.dnsPrimary, server.dnsSecondary)
                .distinct(),
            peerPublicKey = server.publicKey,
            peerPresharedKey = presharedKeyBase64,
            peerEndpoint = "${server.endpointHost}:${server.endpointPort}",
            peerAllowedIps = if (server.supportsIpv6) {
                listOf(KeyUtils.ROUTE_ALL_V4, KeyUtils.ROUTE_ALL_V6)
            } else {
                listOf(KeyUtils.ROUTE_ALL_V4)
            },
            persistentKeepalive = 25
        )
        return WireGuardConfigParser.build(config)
    }
}
