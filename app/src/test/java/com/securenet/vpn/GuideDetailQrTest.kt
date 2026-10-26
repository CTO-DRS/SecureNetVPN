package com.securenet.vpn

import com.google.zxing.common.BitMatrix
import com.securenet.vpn.guide.GuideDetail
import com.securenet.vpn.guide.GuideDetailQr
import com.securenet.vpn.guide.WireGuideCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.8 — tests behind the detail-page QR: the pure [GuideDetailQr.matrix]
 * encoder (no Android runtime involved) and [GuideDetail.hostOf]. The
 * catalog-wide passes are the important ones — a catalog URL that fails to
 * encode or parse must break the build here, not just the screen.
 */
class GuideDetailQrTest {

    private val sample = "https://www.wireguard.com/quickstart/"

    @Test
    fun `matrix comes back at the exact requested size`() {
        val m = GuideDetailQr.matrix(sample, 512)
        assertNotNull(m)
        assertEquals(512, m!!.width)
        assertEquals(512, m.height)
    }

    @Test
    fun `matrix generation is deterministic for the same content`() {
        val a = GuideDetailQr.matrix(sample, 256)!!
        val b = GuideDetailQr.matrix(sample, 256)!!
        assertEquals(a.toString(), b.toString())
    }

    @Test
    fun `matrix actually contains dark modules`() {
        val m: BitMatrix = GuideDetailQr.matrix(sample, 512)!!
        var dark = false
        outer@ for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y)) { dark = true; break@outer }
            }
        }
        assertTrue("an all-light matrix would scan as blank", dark)
    }

    @Test
    fun `blank or invalid content degrades to null instead of crashing`() {
        assertNull(GuideDetailQr.matrix(""))
        assertNull(GuideDetailQr.matrix("   "))
        assertNull(GuideDetailQr.matrix("x", 0))
        assertNull(GuideDetailQr.matrix("x", -5))
    }

    @Test
    fun `a very long url still encodes`() {
        val long = "https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/9/" +
            "html/configuring_and_managing_networking/assembly_setting-up-a-wireguard-vpn" +
            "_configuring-and-managing-networking"
        assertNotNull(GuideDetailQr.matrix(long, 512))
    }

    @Test
    fun `every catalog url encodes into a qr matrix`() {
        WireGuideCatalog.entries.forEach { entry ->
            assertNotNull("catalog url failed to encode: ${entry.id}", 
                GuideDetailQr.matrix(entry.url, 512))
        }
    }

    @Test
    fun `host strips www and lowercases`() {
        assertEquals("nordvpn.com", GuideDetail.hostOf("https://www.nordvpn.com/servers/wireguard/"))
        assertEquals("play.google.com",
            GuideDetail.hostOf("https://play.google.com/store/apps/details?id=com.wireguard.android"))
        assertEquals("wireguard.com", GuideDetail.hostOf("https://WIREGUARD.COM/quickstart/"))
        assertNull(GuideDetail.hostOf("not a url"))
        assertNull(GuideDetail.hostOf(""))
    }

    @Test
    fun `every catalog url yields a readable official host`() {
        WireGuideCatalog.entries.forEach { entry ->
            val host = GuideDetail.hostOf(entry)
            assertNotNull("no host parsed for ${entry.id}", host)
            assertTrue("host for ${entry.id} looks wrong: $host",
                host!!.contains(".") && !host.startsWith("."))
        }
    }
}
