package com.securenet.vpn

import com.securenet.vpn.network.DnsCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsCacheTest {

    private class FakeClock {
        var now: Long = 1_000_000L
        fun tick(ms: Long) {
            now += ms
        }
    }

    @Test
    fun `put then get returns the resolved ips`() {
        val clock = FakeClock()
        val cache = DnsCache(clock = { clock.now })

        cache.put("example.com", listOf("93.184.216.34"))
        assertEquals(listOf("93.184.216.34"), cache.get("example.com"))
        assertEquals(1, cache.stats.hits)
        assertEquals(0, cache.stats.misses)
    }

    @Test
    fun `expired entries miss and count as expirations`() {
        val clock = FakeClock()
        val cache = DnsCache(defaultTtlMs = 60_000, clock = { clock.now })

        cache.put("example.com", listOf("1.2.3.4"))
        clock.tick(60_001)

        assertNull(cache.get("example.com"))
        assertEquals(1, cache.stats.expirations)
        assertEquals(1, cache.stats.misses)
    }

    @Test
    fun `lookup is case insensitive`() {
        val clock = FakeClock()
        val cache = DnsCache(clock = { clock.now })

        cache.put("Example.COM", listOf("5.6.7.8"))
        assertEquals(listOf("5.6.7.8"), cache.get("example.com"))
    }

    @Test
    fun `LRU eviction keeps the cache bounded`() {
        val clock = FakeClock()
        val cache = DnsCache(maxEntries = 3, clock = { clock.now })

        cache.put("a.test", listOf("1.1.1.1"))
        cache.put("b.test", listOf("2.2.2.2"))
        cache.put("c.test", listOf("3.3.3.3"))

        // Touch a.test so b.test becomes the LRU victim.
        cache.get("a.test")

        cache.put("d.test", listOf("4.4.4.4"))

        assertEquals(3, cache.size())
        assertNull(cache.get("b.test"))
        assertEquals(listOf("1.1.1.1"), cache.get("a.test"))
        assertEquals(1, cache.stats.evictions)
    }

    @Test
    fun `hit rate is correct after mixed traffic`() {
        val clock = FakeClock()
        val cache = DnsCache(clock = { clock.now })

        cache.put("x.test", listOf("9.9.9.9"))
        cache.get("x.test")          // hit
        cache.get("x.test")          // hit
        cache.get("missing.test")    // miss

        // 2 hits out of 3 lookups (puts are writes, not lookups).
        assertEquals(2.0 / 3.0, cache.hitRate(), 0.0001)
    }

    @Test
    fun `invalidate removes a single entry`() {
        val clock = FakeClock()
        val cache = DnsCache(clock = { clock.now })

        cache.put("y.test", listOf("7.7.7.7"))
        cache.invalidate("y.test")
        assertNull(cache.get("y.test"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `empty ip lists are rejected`() {
        val clock = FakeClock()
        val cache = DnsCache(clock = { clock.now })
        cache.put("empty.test", emptyList())
        assertNull(cache.get("empty.test"))
        assertTrue(cache.size() == 0)
    }
}
