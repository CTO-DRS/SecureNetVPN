package com.securenet.vpn

import com.securenet.vpn.intelligence.NetworkTrustEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTrustEngineTest {

    private val engine = NetworkTrustEngine()
    private val homeWifi = NetworkTrustEngine.Fingerprint("wifi", false)
    private val officeWifi = NetworkTrustEngine.Fingerprint("wifi", true)
    private val cellular = NetworkTrustEngine.Fingerprint("cellular", true)

    @Test
    fun `disabled rules never connect`() {
        assertEquals(
            NetworkTrustEngine.Action.NONE,
            engine.decide(cellular, emptyList(), smartRulesEnabled = false, connected = false, hasInternet = true)
        )
    }

    @Test
    fun `connected tunnel is never re-triggered`() {
        assertEquals(
            NetworkTrustEngine.Action.NONE,
            engine.decide(cellular, emptyList(), smartRulesEnabled = true, connected = true, hasInternet = true)
        )
    }

    @Test
    fun `no internet means no auto-connect`() {
        assertEquals(
            NetworkTrustEngine.Action.NONE,
            engine.decide(cellular, emptyList(), smartRulesEnabled = true, connected = false, hasInternet = false)
        )
    }

    @Test
    fun `untrusted cellular triggers connect`() {
        assertEquals(
            NetworkTrustEngine.Action.CONNECT,
            engine.decide(cellular, emptyList(), smartRulesEnabled = true, connected = false, hasInternet = true)
        )
    }

    @Test
    fun `untrusted wifi triggers connect`() {
        assertEquals(
            NetworkTrustEngine.Action.CONNECT,
            engine.decide(homeWifi, emptyList(), smartRulesEnabled = true, connected = false, hasInternet = true)
        )
    }

    @Test
    fun `trusted wifi stays quiet`() {
        assertEquals(
            NetworkTrustEngine.Action.NONE,
            engine.decide(homeWifi, listOf(homeWifi), smartRulesEnabled = true, connected = false, hasInternet = true)
        )
    }

    @Test
    fun `trust is fingerprint-specific`() {
        assertEquals(
            NetworkTrustEngine.Action.CONNECT,
            engine.decide(officeWifi, listOf(homeWifi), smartRulesEnabled = true, connected = false, hasInternet = true)
        )
    }

    @Test
    fun `json roundtrip preserves fingerprints`() {
        val json = engine.trustListToJson(listOf(homeWifi, cellular))
        val restored = engine.trustListFromJson(json)
        assertEquals(listOf(homeWifi, cellular), restored)
    }

    @Test
    fun `malformed json yields empty list`() {
        assertTrue(engine.trustListFromJson(null).isEmpty())
        assertTrue(engine.trustListFromJson("").isEmpty())
        assertTrue(engine.trustListFromJson("not json").isEmpty())
        assertTrue(engine.trustListFromJson("{\"t\":\"wifi\"}").isEmpty())
    }
}
