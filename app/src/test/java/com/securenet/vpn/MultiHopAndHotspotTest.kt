package com.securenet.vpn

import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.hotspot.HotspotLeakRisk
import com.securenet.vpn.hotspot.HotspotShare
import com.securenet.vpn.multihop.MultiHopChain
import com.securenet.vpn.multihop.MultiHopEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4: Multi-hop chain + hotspot sharing logic tests.
 */
class MultiHopAndHotspotTest {

    private fun server(id: String, name: String, city: String, host: String) = VpnServer(
        id = id, name = name, countryCode = "XX", city = city,
        endpointHost = host, endpointPort = 51820,
        publicKey = "pubkey-$id"
    )

    private val warsaw = server("s1", "Warsaw 01", "Warsaw", "w1.securenet.io")
    private val zurich = server("s2", "Zurich 02", "Zurich", "z2.securenet.io")

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    fun `valid chain passes validation`() {
        assertEquals(
            MultiHopEngine.Validation.OK,
            MultiHopEngine.validate(warsaw, zurich)
        )
    }

    @Test
    fun `missing entry or exit is rejected`() {
        assertEquals(
            MultiHopEngine.Validation.MISSING_ENTRY,
            MultiHopEngine.validate(null, zurich)
        )
        assertEquals(
            MultiHopEngine.Validation.MISSING_EXIT,
            MultiHopEngine.validate(warsaw, null)
        )
    }

    @Test
    fun `same server for both hops is rejected`() {
        assertEquals(
            MultiHopEngine.Validation.SAME_SERVER,
            MultiHopEngine.validate(warsaw, warsaw)
        )
    }

    // ------------------------------------------------------------------
    // Chain plan
    // ------------------------------------------------------------------

    @Test
    fun `plan points transport at the entry relay and keys at the exit`() {
        val plan = MultiHopEngine.plan(warsaw, zurich, outerMtu = 1420)!!
        assertEquals("w1.securenet.io", plan.relayHost)
        assertEquals(51820, plan.relayPort)
        assertEquals("s2", plan.exit.id)
        assertEquals(zurich.publicKey, plan.exit.publicKey)
        assertEquals("Warsaw → Zurich", plan.label)
    }

    @Test
    fun `plan is null for invalid chains`() {
        assertNull(MultiHopEngine.plan(warsaw, warsaw))
        assertNull(MultiHopEngine.plan(null, zurich))
        assertNull(MultiHopEngine.plan(warsaw, null))
    }

    // ------------------------------------------------------------------
    // MTU math for the nested hop
    // ------------------------------------------------------------------

    @Test
    fun `inner mtu shrinks by the hop overhead`() {
        assertEquals(1340, MultiHopEngine.innerMtu(1420))
        assertEquals(1420, MultiHopEngine.innerMtu(1420) + MultiHopEngine.HOP_OVERHEAD_BYTES)
    }

    @Test
    fun `inner mtu never drops below the safety floor`() {
        assertEquals(1200, MultiHopEngine.innerMtu(1000))
    }

    // ------------------------------------------------------------------
    // JSON round-trip
    // ------------------------------------------------------------------

    @Test
    fun `chain json round-trips with server payloads`() {
        val original = MultiHopChain(
            enabled = true,
            entryJson = warsaw.toVpnServerJson(),
            exitJson = zurich.toVpnServerJson()
        )
        val parsed = MultiHopChain.fromJson(original.toJson())
        assertTrue(parsed.enabled)
        assertEquals("s1", parsed.entryServer()?.id)
        assertEquals("s2", parsed.exitServer()?.id)
    }

    @Test
    fun `garbage chain json degrades to disabled`() {
        val fallback = MultiHopChain.fromJson("{not json")
        assertFalse(fallback.enabled)
        assertNull(fallback.entryServer())
        assertNull(fallback.exitServer())
    }

    @Test
    fun `describe covers off and complete and incomplete`() {
        assertEquals("off", MultiHopEngine.describe(MultiHopChain()))
        assertEquals(
            "Warsaw → Zurich",
            MultiHopEngine.describe(
                MultiHopChain(true, warsaw.toVpnServerJson(), zurich.toVpnServerJson())
            )
        )
        assertEquals(
            "incomplete",
            MultiHopEngine.describe(MultiHopChain(true, warsaw.toVpnServerJson(), ""))
        )
    }

    // ------------------------------------------------------------------
    // Hotspot leak-risk matrix
    // ------------------------------------------------------------------

    @Test
    fun `no tethering means no leak risk`() {
        assertEquals(
            HotspotLeakRisk.NONE,
            HotspotShare.leakRisk(tetherActive = false, vpnConnected = false, killSwitchEnabled = false)
        )
        assertEquals(
            HotspotLeakRisk.NONE,
            HotspotShare.leakRisk(tetherActive = false, vpnConnected = true, killSwitchEnabled = false)
        )
    }

    @Test
    fun `tether with vpn and kill switch is the safe combination`() {
        assertEquals(
            HotspotLeakRisk.LOW,
            HotspotShare.leakRisk(tetherActive = true, vpnConnected = true, killSwitchEnabled = true)
        )
    }

    @Test
    fun `tether without kill switch is high risk even with vpn`() {
        assertEquals(
            HotspotLeakRisk.HIGH,
            HotspotShare.leakRisk(tetherActive = true, vpnConnected = true, killSwitchEnabled = false)
        )
    }

    @Test
    fun `tether without vpn always exposes clients`() {
        assertEquals(
            HotspotLeakRisk.HIGH,
            HotspotShare.leakRisk(tetherActive = true, vpnConnected = false, killSwitchEnabled = true)
        )
        assertEquals(
            HotspotLeakRisk.HIGH,
            HotspotShare.leakRisk(tetherActive = true, vpnConnected = false, killSwitchEnabled = false)
        )
    }

    // ------------------------------------------------------------------
    // Tether iface parsing + describe
    // ------------------------------------------------------------------

    @Test
    fun `tether state from iface list`() {
        assertTrue(HotspotShare.parseTetheredIfaces(listOf("wlan0", "rndis0")))
        assertTrue(HotspotShare.parseTetheredIfaces(listOf("rndis0")))
        assertFalse(HotspotShare.parseTetheredIfaces(emptyList()))
        assertFalse(HotspotShare.parseTetheredIfaces(listOf("", "")))
    }

    @Test
    fun `describe summarizes hotspot state`() {
        assertEquals("off", HotspotShare.describe(tetherActive = false, vpnConnected = true))
        assertEquals("active", HotspotShare.describe(tetherActive = true, vpnConnected = false))
        assertEquals("active+vpn", HotspotShare.describe(tetherActive = true, vpnConnected = true))
    }

    // ------------------------------------------------------------------
    // Constants sanity
    // ------------------------------------------------------------------

    @Test
    fun `hop overhead is a positive wireguard-class number`() {
        assertTrue(MultiHopEngine.HOP_OVERHEAD_BYTES in 40..120)
    }

    @Test
    fun `tether settings action is a valid component name`() {
        assertTrue(HotspotShare.TETHER_SETTINGS_ACTION.startsWith("com.android.settings"))
        assertEquals(
            "android.settings.WIRELESS_SETTINGS",
            HotspotShare.WIRELESS_SETTINGS_ACTION
        )
    }
}
