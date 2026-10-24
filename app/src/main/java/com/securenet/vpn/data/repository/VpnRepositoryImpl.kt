package com.securenet.vpn.data.repository

import com.securenet.vpn.data.local.ConnectionLogger
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.ConnectionStats
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.domain.repository.VpnRepository
import com.securenet.vpn.network.DnsCacheManager
import com.securenet.vpn.network.MtuOptimizer
import com.securenet.vpn.service.SecureNetVpnService
import com.securenet.vpn.util.KeyUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production implementation. Bridges the UI/domain layers to the foreground
 * VPN service via a static bridge (services and activities live in
 * different lifecycles, so the service owns the source of truth).
 */
class VpnRepositoryImpl(
    private val context: android.content.Context,
    private val securePrefs: SecurePrefs,
    private val mtuOptimizer: MtuOptimizer,
    private val dnsCacheManager: DnsCacheManager,
    private val logger: ConnectionLogger
) : VpnRepository {

    override val state: StateFlow<ConnectionState> = SecureNetVpnService.state

    override val stats: StateFlow<ConnectionStats> = SecureNetVpnService.stats

    override suspend fun connect(server: VpnServer): Result<Unit> {
        if (server.publicKey.isBlank() || server.endpointHost.isBlank()) {
            logger.appendEvent(ConnectionState.FAILED, server.id, "invalid_server_config")
            return Result.failure(IllegalArgumentException("Server configuration incomplete"))
        }

        // The service owns MTU computation (auto-tuning + WireGuard overhead).
        val ipv6 = server.supportsIpv6
        val settings = KeyUtils.buildWgUserspaceSettings(
            privateKeyBase64 = obtainDevicePrivateKey(),
            peerPublicKeyBase64 = server.publicKey,
            endpointHost = server.endpointHost,
            endpointPort = server.endpointPort,
            allowedIps = if (ipv6) {
                listOf(KeyUtils.ROUTE_ALL_V4, KeyUtils.ROUTE_ALL_V6)
            } else {
                listOf(KeyUtils.ROUTE_ALL_V4)
            }
        )

        return try {
            SecureNetVpnService.start(
                context = context,
                server = server,
                wgSettings = settings,
                mtu = mtuOptimizer.recommendedMtu(useIpv6 = ipv6),
                dnsServers = listOf(server.dnsPrimary, server.dnsSecondary),
                localAddress = server.localAddressV4,
                localPrefix = server.localPrefixV4
            )
            securePrefs.cachedSelectedServer = server.toVpnServerJson()
            Result.success(Unit)
        } catch (e: Exception) {
            logger.appendEvent(ConnectionState.FAILED, server.id, e.message)
            Result.failure(e)
        }
    }

    override suspend fun disconnect(): Result<Unit> = try {
        SecureNetVpnService.stop(context)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun reconnect(): Result<Unit> = try {
        SecureNetVpnService.requestReconnect(context)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun setBatterySaver(enabled: Boolean) {
        securePrefs.batterySaverMode = enabled
        SecureNetVpnService.notifyBatterySaverChanged(context)
    }

    /**
     * Private keys are never hardcoded and never shipped with the app.
     * On first activation a fresh key is generated (32 secure-random bytes,
     * RFC 7748 clamped) and stored in EncryptedSharedPreferences; the
     * matching public key is derived inside the wireguard-go backend.
     */
    private fun obtainDevicePrivateKey(): String {
        securePrefs.devicePrivateKey?.let { return it }
        val random = java.security.SecureRandom()
        val keyBytes = ByteArray(KeyUtils.KEY_SIZE).also { random.nextBytes(it) }
        val privateKeyB64 = KeyUtils.toBase64Key(KeyUtils.clampPrivateKey(keyBytes))
        securePrefs.devicePrivateKey = privateKeyB64
        return privateKeyB64
    }
}
