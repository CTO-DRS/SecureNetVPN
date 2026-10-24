package com.securenet.vpn

import com.securenet.vpn.intelligence.DnsShieldEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsShieldEngineTest {

    private val engine = DnsShieldEngine()

    @Test
    fun `blocks known ad domain exactly`() {
        val d = engine.classify("doubleclick.net")
        assertTrue(d.blocked)
        assertEquals(DnsShieldEngine.Category.ADS, d.category)
    }

    @Test
    fun `blocks subdomains of listed rules`() {
        val d = engine.classify("ad.doubleclick.net")
        assertTrue(d.blocked)
        assertEquals(DnsShieldEngine.Category.ADS, d.category)
    }

    @Test
    fun `trackers outrank ads when both could match`() {
        val d = engine.classify("scorecardresearch.com")
        assertTrue(d.blocked)
        assertEquals(DnsShieldEngine.Category.TRACKERS, d.category)
    }

    @Test
    fun `malware has highest priority`() {
        val d = engine.classify("zeusclicks.com")
        assertTrue(d.blocked)
        assertEquals(DnsShieldEngine.Category.MALWARE, d.category)
    }

    @Test
    fun `case and trailing dot are normalized`() {
        assertTrue(engine.classify("HotJar.COM.").blocked)
    }

    @Test
    fun `allows normal domains`() {
        val d = engine.classify("wikipedia.org")
        assertFalse(d.blocked)
        assertNull(d.category)
    }

    @Test
    fun `similar but different domain is not blocked`() {
        assertFalse(engine.classify("notdoubleclick.net.evil.example").blocked)
    }

    @Test
    fun `empty and blank hosts pass`() {
        assertFalse(engine.classify("").blocked)
        assertFalse(engine.classify("   ").blocked)
    }

    @Test
    fun `deep subdomain still matches rule`() {
        assertTrue(engine.classify("a.b.c.ads.yahoo.com").blocked)
    }

    @Test
    fun `filtering resolvers are provided`() {
        assertEquals(2, DnsShieldEngine.SHIELD_DNS_V4.size)
    }
}
