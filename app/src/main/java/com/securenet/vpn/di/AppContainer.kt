package com.securenet.vpn.di

import android.content.Context
import com.securenet.vpn.data.local.ConnectionLogger
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.data.remote.RemoteConfigApi
import com.securenet.vpn.data.repository.RemoteConfigRepositoryImpl
import com.securenet.vpn.data.repository.ScannerRepositoryImpl
import com.securenet.vpn.data.repository.ServerRepositoryImpl
import com.securenet.vpn.data.repository.VpnRepositoryImpl
import com.securenet.vpn.domain.repository.RemoteConfigRepository
import com.securenet.vpn.domain.repository.ScannerRepository
import com.securenet.vpn.domain.repository.ServerRepository
import com.securenet.vpn.domain.repository.VpnRepository
import com.securenet.vpn.domain.usecase.ConnectVpnUseCase
import com.securenet.vpn.domain.usecase.DisconnectVpnUseCase
import com.securenet.vpn.domain.usecase.ObserveVpnStateUseCase
import com.securenet.vpn.domain.usecase.ScanDeviceUseCase
import com.securenet.vpn.network.DnsCacheManager
import com.securenet.vpn.network.MtuOptimizer
import com.securenet.vpn.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Lightweight manual DI container (Google-recommended AppContainer pattern).
 * Everything is lazily constructed so cold start stays fast.
 */
class AppContainer(context: Context) {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val securePrefs: SecurePrefs by lazy { SecurePrefs(context) }

    val connectionLogger: ConnectionLogger by lazy { ConnectionLogger(context) }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(context) }

    val mtuOptimizer: MtuOptimizer by lazy { MtuOptimizer() }

    val dnsCacheManager: DnsCacheManager by lazy { DnsCacheManager(applicationScope, securePrefs) }

    private val remoteConfigApi: RemoteConfigApi by lazy { RemoteConfigApi() }

    val remoteConfigRepository: RemoteConfigRepository by lazy {
        RemoteConfigRepositoryImpl(remoteConfigApi, securePrefs)
    }

    val serverRepository: ServerRepository by lazy {
        ServerRepositoryImpl(remoteConfigRepository, securePrefs)
    }

    val vpnRepository: VpnRepository by lazy {
        VpnRepositoryImpl(context, securePrefs, mtuOptimizer, dnsCacheManager, connectionLogger)
    }

    val scannerRepository: ScannerRepository by lazy {
        ScannerRepositoryImpl(context, securePrefs)
    }

    val connectVpnUseCase: ConnectVpnUseCase by lazy {
        ConnectVpnUseCase(vpnRepository, connectionLogger)
    }

    val disconnectVpnUseCase: DisconnectVpnUseCase by lazy {
        DisconnectVpnUseCase(vpnRepository, connectionLogger)
    }

    val observeVpnStateUseCase: ObserveVpnStateUseCase by lazy {
        ObserveVpnStateUseCase(vpnRepository)
    }

    val scanDeviceUseCase: ScanDeviceUseCase by lazy {
        ScanDeviceUseCase(scannerRepository, connectionLogger)
    }

    // ------------------------------------------------------------------
    // v1.1: local intelligence engines
    // ------------------------------------------------------------------

    val serverRecommender: com.securenet.vpn.intelligence.ServerRecommender by lazy {
        com.securenet.vpn.intelligence.ServerRecommender()
    }

    val latencyProber: com.securenet.vpn.intelligence.ServerLatencyProber by lazy {
        com.securenet.vpn.intelligence.ServerLatencyProber()
    }

    val securityScoreEngine: com.securenet.vpn.intelligence.SecurityScoreEngine by lazy {
        com.securenet.vpn.intelligence.SecurityScoreEngine()
    }

    val speedTestEngine: com.securenet.vpn.intelligence.SpeedTestEngine by lazy {
        com.securenet.vpn.intelligence.SpeedTestEngine()
    }
}
