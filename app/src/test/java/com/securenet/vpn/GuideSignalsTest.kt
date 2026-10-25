package com.securenet.vpn

import com.securenet.vpn.guide.GuideSignalsCodec
import com.securenet.vpn.guide.GuideSignalsData
import com.securenet.vpn.guide.GuideSignalsMerge
import com.securenet.vpn.guide.InMemoryGuideStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.2 — the guide-signals payload that rides the encrypted `.snback`
 * backup: hand-rolled JSON codec (single-line guarantee, escaping, tolerance
 * to malformed input) and the non-destructive merge applied on restore.
 */
class GuideSignalsTest {

    // ------------------------------------------------------------------
    // Codec
    // ------------------------------------------------------------------

    @Test
    fun `codec round trip survives tricky note text`() {
        val tricky = "quote \" backslash \\ newline \n tab \t Arabic \"ملاحظة\" }{braces}"
        val signals = GuideSignalsData(
            ratings = mapOf("mullvad" to 5, "ivpn" to 3),
            favorites = setOf("mullvad", "wg-android"),
            notes = mapOf("ivpn" to tricky)
        )
        val json = GuideSignalsCodec.encode(signals)
        // A raw newline inside the header line would corrupt the container.
        assertFalse(json.contains('\n'))
        assertFalse(json.contains('\r'))
        assertEquals(signals, GuideSignalsCodec.decode(json))
    }

    @Test
    fun `codec writes the documented wire shape`() {
        val json = GuideSignalsCodec.encode(
            GuideSignalsData(
                ratings = mapOf("mullvad" to 5),
                favorites = setOf("mullvad"),
                notes = mapOf("ivpn" to "fast")
            )
        )
        assertEquals(
            "{\"v\":1,\"ratings\":{\"mullvad\":5}," +
                "\"favorites\":[\"mullvad\"],\"notes\":{\"ivpn\":\"fast\"}}",
            json
        )
    }

    @Test
    fun `codec encode always clamps ratings into range`() {
        val json = GuideSignalsCodec.encode(
            GuideSignalsData(ratings = mapOf("a" to 9, "b" to -2))
        )
        val decoded = GuideSignalsCodec.decode(json)!!
        assertEquals(5, decoded.ratings["a"])
        assertEquals(1, decoded.ratings["b"])
    }

    @Test
    fun `codec decode of hand written json`() {
        val decoded = GuideSignalsCodec.decode(
            "{\"v\":1,\"ratings\":{\"mullvad\":4},\"favorites\":[],\"notes\":{}}"
        )!!
        assertEquals(mapOf("mullvad" to 4), decoded.ratings)
        assertTrue(decoded.favorites.isEmpty())
        assertTrue(decoded.notes.isEmpty())
    }

    @Test
    fun `codec decode is null on garbage unbalanced or trailing junk`() {
        assertNull(GuideSignalsCodec.decode("not json at all"))
        assertNull(GuideSignalsCodec.decode("{\"v\":1"))
        assertNull(GuideSignalsCodec.decode("{\"v\":1} trailing"))
        assertNull(GuideSignalsCodec.decode(""))
    }

    @Test
    fun `codec decode skips unknown members for forward compatibility`() {
        val decoded = GuideSignalsCodec.decode(
            "{\"v\":1,\"future\":{\"x\":[1,2,{\"y\":\"z\"}]}," +
                "\"ratings\":{\"mullvad\":2},\"later\":\"s\\\"t\"}"
        )!!
        assertEquals(mapOf("mullvad" to 2), decoded.ratings)
    }

    @Test
    fun `codec decode drops invalid ratings blank notes and blank ids`() {
        val decoded = GuideSignalsCodec.decode(
            "{\"v\":1," +
                "\"ratings\":{\"a\":0,\"b\":6,\"c\":3,\"d\":-1}," +
                "\"favorites\":[\"ok\",\"\",\"  \"]," +
                "\"notes\":{\"a\":\"kept\",\"b\":\"   \",\"c\":\"\"}}"
        )!!
        assertEquals(mapOf("c" to 3), decoded.ratings)
        assertEquals(setOf("ok"), decoded.favorites)
        assertEquals(mapOf("a" to "kept"), decoded.notes)
    }

    @Test
    fun `empty payload reports emptiness and zero count`() {
        val empty = GuideSignalsData()
        assertTrue(empty.isEmpty())
        assertEquals(0, empty.signalCount())
        assertFalse(GuideSignalsData(favorites = setOf("mullvad")).isEmpty())
        assertEquals(3, GuideSignalsData(
            ratings = mapOf("a" to 1), favorites = setOf("b"), notes = mapOf("c" to "n")
        ).signalCount())
    }

    // ------------------------------------------------------------------
    // Snapshot
    // ------------------------------------------------------------------

    @Test
    fun `snapshot reads every signal kind from the store`() {
        val store = InMemoryGuideStore()
        store.setRating("mullvad", 5)
        store.setRating("dropped", 0)      // cleared → must not appear
        store.setFavorite("wg-android", true)
        store.setNote("ivpn", "fast at night")
        val snapshot = GuideSignalsData.snapshot(store)

        assertEquals(mapOf("mullvad" to 5), snapshot.ratings)
        assertEquals(setOf("wg-android"), snapshot.favorites)
        assertEquals(mapOf("ivpn" to "fast at night"), snapshot.notes)
    }

    @Test
    fun `snapshot of an untouched store is empty`() {
        assertTrue(GuideSignalsData.snapshot(InMemoryGuideStore()).isEmpty())
    }

    // ------------------------------------------------------------------
    // Merge (non-destructive restore semantics)
    // ------------------------------------------------------------------

    @Test
    fun `merge fills gaps from the backup and never overwrites local`() {
        val local = GuideSignalsData(
            ratings = mapOf("mullvad" to 3, "ivpn" to 4),
            favorites = setOf("ivpn"),
            notes = mapOf("mullvad" to "mine")
        )
        val imported = GuideSignalsData(
            ratings = mapOf("mullvad" to 5, "tailscale" to 2),
            favorites = setOf("mullvad", "ivpn"),
            notes = mapOf("mullvad" to "from backup", "wg-easy" to "docker")
        )
        val result = GuideSignalsMerge.merge(local, imported)

        // Local values win wherever they exist.
        assertEquals(3, result.applied.ratings["mullvad"])
        assertEquals("mine", result.applied.notes["mullvad"])
        // Gaps are filled from the backup.
        assertEquals(2, result.applied.ratings["tailscale"])
        assertEquals("docker", result.applied.notes["wg-easy"])
        assertTrue("mullvad" in result.applied.favorites)
        assertTrue("ivpn" in result.applied.favorites)
    }

    @Test
    fun `merge counts kept local values and touched ids`() {
        val local = GuideSignalsData(
            ratings = mapOf("mullvad" to 3),
            favorites = setOf("ivpn"),
            notes = mapOf("wg-easy" to "home")
        )
        val imported = GuideSignalsData(
            ratings = mapOf("mullvad" to 5, "ivpn" to 4),
            favorites = setOf("ivpn", "mullvad"),
            notes = mapOf("wg-easy" to "other", "pivpn" to "raspberry")
        )
        val result = GuideSignalsMerge.merge(local, imported)

        // kept: mullvad rating + ivpn favorite + wg-easy note = 3
        assertEquals(3, result.keptLocal)
        // touched: ivpn rating + mullvad favorite + pivpn note = 3 ids? ivpn twice → distinct = 3
        assertEquals(setOf("ivpn", "mullvad", "pivpn"), result.touchedIds)
    }

    @Test
    fun `merge with empty local applies the backup verbatim`() {
        val imported = GuideSignalsData(
            ratings = mapOf("mullvad" to 5),
            favorites = setOf("ivpn"),
            notes = mapOf("wg-easy" to "docker")
        )
        val result = GuideSignalsMerge.merge(GuideSignalsData(), imported)
        assertEquals(imported, result.applied)
        assertEquals(imported.signalCount(), result.touchedIds.size)
        assertEquals(0, result.keptLocal)
    }

    @Test
    fun `merge with empty backup keeps local untouched`() {
        val local = GuideSignalsData(
            ratings = mapOf("mullvad" to 5),
            favorites = setOf("ivpn"),
            notes = mapOf("wg-easy" to "docker")
        )
        val result = GuideSignalsMerge.merge(local, GuideSignalsData())
        assertEquals(local, result.applied)
        assertTrue(result.touchedIds.isEmpty())
        assertEquals(local.signalCount(), result.keptLocal)
    }

    @Test
    fun `applyTo writes merged signals back into the store`() {
        val store = InMemoryGuideStore()
        val applied = GuideSignalsData(
            ratings = mapOf("mullvad" to 5, "ivpn" to 2),
            favorites = setOf("wg-android"),
            notes = mapOf("tailscale" to "mesh")
        )
        val written = GuideSignalsMerge.applyTo(store, applied)

        assertEquals(5, store.rating("mullvad"))
        assertEquals(2, store.rating("ivpn"))
        assertTrue(store.isFavorite("wg-android"))
        assertEquals("mesh", store.note("tailscale"))
        assertEquals(written, applied.signalCount())
        // And a fresh snapshot of the store round-trips to the same data.
        assertEquals(applied, GuideSignalsData.snapshot(store))
    }
}
