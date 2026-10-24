package com.securenet.vpn

import com.securenet.vpn.data.local.VpnEventLogger
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.data.model.ConnectionStats
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.domain.repository.VpnRepository
import com.securenet.vpn.domain.usecase.ConnectVpnUseCase
import com.securenet.vpn.domain.usecase.DisconnectVpnUseCase
import com.securenet.vpn.util.KeyUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Use-case tests with a fake VpnRepository and an in-memory logger.
 * Verifies validation, logging and Result propagation with zero Android
 * dependencies (pure JVM).
 */
class ConnectVpnUseCaseTest {

    private val sampleServer = VpnServer(
        id = "nl-1",
        name = "Amsterdam",
        countryCode = "NL",
        city = "Amsterdam",
        endpointHost = "nl-1.securenet.dev",
        endpointPort = 51820,
        publicKey = KeyUtils.toBase64Key(ByteArray(32) { (it + 1).toByte() })
    )

    private class FakeVpnRepository : VpnRepository {
        var connectCalls = 0
        var disconnectCalls = 0
        var failNextConnect = false

        override val state = MutableStateFlow(ConnectionState.DISCONNECTED)
        override val stats = MutableStateFlow(ConnectionStats.EMPTY)

        override suspend fun connect(server: VpnServer): Result<Unit> {
            connectCalls++
            return if (failNextConnect) {
                Result.failure(IllegalStateException("boom"))
            } else {
                Result.success(Unit)
            }
        }

        override suspend fun disconnect(): Result<Unit> {
            disconnectCalls++
            return Result.success(Unit)
        }

        override suspend fun reconnect(): Result<Unit> = Result.success(Unit)

        override fun setBatterySaver(enabled: Boolean) {}
    }

    private class RecordingLogger : VpnEventLogger {
        val events = mutableListOf<Pair<String, String?>>()

        override fun appendEvent(state: ConnectionState, serverId: String?, detail: String?) {
            events += state.name.lowercase() to (serverId ?: detail)
        }

        override fun appendCustom(event: String, detail: String?) {
            events += event to detail
        }

        override fun appendUsage(dayEpoch: Long, rxBytes: Long, txBytes: Long, connectedSeconds: Long) {
            events += "usage" to "day=$dayEpoch"
        }

        override fun clear() {
            events.clear()
        }
    }

    @Test
    fun `successful connect logs the connecting event`() = runTest {
        val repo = FakeVpnRepository()
        val logger = RecordingLogger()
        val useCase = ConnectVpnUseCase(repo, logger)

        val result = useCase(sampleServer)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.connectCalls)
        assertTrue(logger.events.any { it.first == "connecting" })
    }

    @Test
    fun `missing peer public key fails before touching the repository`() = runTest {
        val repo = FakeVpnRepository()
        val logger = RecordingLogger()
        val useCase = ConnectVpnUseCase(repo, logger)

        val broken = sampleServer.copy(publicKey = "")
        val result = useCase(broken)

        assertTrue(result.isFailure)
        assertEquals(0, repo.connectCalls)
        assertTrue(logger.events.any { it.first == "failed" })
    }

    @Test
    fun `repository failure is propagated and logged`() = runTest {
        val repo = FakeVpnRepository().apply { failNextConnect = true }
        val logger = RecordingLogger()
        val useCase = ConnectVpnUseCase(repo, logger)

        val result = useCase(sampleServer)

        assertTrue(result.isFailure)
        assertEquals(1, repo.connectCalls)
        assertTrue(logger.events.any { it.first == "failed" })
    }

    @Test
    fun `disconnect propagates success and logs`() = runTest {
        val repo = FakeVpnRepository()
        val logger = RecordingLogger()
        val useCase = DisconnectVpnUseCase(repo, logger)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, repo.disconnectCalls)
        assertTrue(logger.events.any { it.first == "disconnected" })
    }
}
