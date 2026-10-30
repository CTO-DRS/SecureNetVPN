package com.securenet.vpn

import com.securenet.vpn.guide.GuideSearchHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.20 — the guide's recent-search history. The pure core must behave
 * like a well-kept MRU list: newest first, blanks ignored, duplicates
 * collapse into the front slot, the cap holds no matter how much is fed
 * in, and the storage round-trip survives the unit separator without ever
 * producing a blank entry.
 */
class GuideSearchHistoryTest {

    @Test
    fun `record puts the newest query first`() {
        val history = GuideSearchHistory.record(listOf("mullvad"), "  mesh  ")
        assertEquals(listOf("mesh", "mullvad"), history)
    }

    @Test
    fun `record trims and ignores blank queries`() {
        assertEquals(listOf("mullvad"), GuideSearchHistory.record(listOf("mullvad"), "   "))
        assertEquals(emptyList<String>(), GuideSearchHistory.record(emptyList(), ""))
        // whitespace-only input leaves the list untouched (same instance semantics)
        val untouched = listOf("a", "b")
        assertTrue(GuideSearchHistory.record(untouched, " \t ") === untouched)
    }

    @Test
    fun `record collapses duplicates case-insensitively with the fresh spelling winning`() {
        val history = GuideSearchHistory.record(
            listOf("mullvad", "wireguard"), "MULLVAD"
        )
        assertEquals(listOf("MULLVAD", "wireguard"), history)
        val again = GuideSearchHistory.record(history, "WireGuard")
        assertEquals(listOf("WireGuard", "MULLVAD"), again)
    }

    @Test
    fun `record caps the list at MAX_ENTRIES keeping the newest`() {
        var history = listOf("q1", "q2", "q3", "q4", "q5", "q6", "q7", "q8")
        assertEquals(GuideSearchHistory.MAX_ENTRIES, history.size)
        history = GuideSearchHistory.record(history, "q9")
        assertEquals(GuideSearchHistory.MAX_ENTRIES, history.size)
        assertEquals(listOf("q9", "q1", "q2", "q3", "q4", "q5", "q6", "q7"), history)
        assertTrue(history.contains("q8").not())
    }

    @Test
    fun `record on an empty history starts the list`() {
        assertEquals(listOf("مجاني"), GuideSearchHistory.record(emptyList(), "مجاني"))
    }

    @Test
    fun `encode and decode round-trip the history`() {
        val history = listOf("mesh", "مجاني", "Mullvad")
        val encoded = GuideSearchHistory.encode(history)
        assertTrue(encoded.contains('\u001F'))
        assertEquals(history, GuideSearchHistory.decode(encoded))
    }

    @Test
    fun `decode tolerates null, blank and separator-only strings`() {
        assertEquals(emptyList<String>(), GuideSearchHistory.decode(null))
        assertEquals(emptyList<String>(), GuideSearchHistory.decode(""))
        assertEquals(emptyList<String>(), GuideSearchHistory.decode("\u001F\u001F"))
        // blank entries inside a real list are dropped, real ones survive
        assertEquals(
            listOf("mesh"),
            GuideSearchHistory.decode(" \u001F mesh \u001F ")
        )
    }

    @Test
    fun `encode of an empty history is an empty string`() {
        assertEquals("", GuideSearchHistory.encode(emptyList()))
        assertEquals(emptyList<String>(), GuideSearchHistory.decode(GuideSearchHistory.encode(emptyList())))
    }

    @Test
    fun `arabic queries survive the round-trip byte for byte`() {
        val history = listOf("مجاني", "شبكات mesh", "راوتر")
        assertEquals(history, GuideSearchHistory.decode(GuideSearchHistory.encode(history)))
    }
}
