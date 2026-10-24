package com.securenet.vpn

import com.securenet.vpn.backup.LogBackupEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.6 — Encrypted log backup engine: container round-trip, wrong
 * passphrase / tamper rejection, and the non-destructive merge used by
 * the restore path.
 */
class LogBackupTest {

    private val pass = "correct horse".toCharArray()
    private val wrong = "wrong horse".toCharArray()

    private fun line(ts: String, event: String, server: String? = null) =
        "{\"ts\":\"$ts\",\"event\":\"$event\"" +
            (server?.let { ",\"server\":\"$it\"" } ?: "") + "}"

    // ------------------------------------------------------------------
    // Round-trip
    // ------------------------------------------------------------------

    @Test
    fun `round trip preserves every event line`() {
        val events = listOf(
            line("2026-10-01T08:00:00.000+0000", "connected", "de-1"),
            line("2026-10-01T09:30:00.000+0000", "disconnected"),
            "{\"ts\":\"2026-10-02T10:00:00.000+0000\",\"event\":\"usage\",\"day\":20362,\"rx\":100,\"tx\":50,\"connected_s\":5400}"
        )
        val blob = LogBackupEngine.encode(events, pass)
        val payload = LogBackupEngine.decode(blob, pass)

        assertEquals(events, payload.eventLines)
        assertTrue(payload.createdAtMillis > 0)
    }

    @Test
    fun `round trip with empty log yields zero events`() {
        val blob = LogBackupEngine.encode(emptyList(), pass)
        val payload = LogBackupEngine.decode(blob, pass)
        assertTrue(payload.eventLines.isEmpty())
    }

    @Test
    fun `encode drops blank lines`() {
        val blob = LogBackupEngine.encode(listOf("   ", "", "\n"), pass)
        val payload = LogBackupEngine.decode(blob, pass)
        assertTrue(payload.eventLines.isEmpty())
    }

    @Test
    fun `ciphertexts with the same input differ (random salt and nonce)`() {
        val events = listOf(line("2026-10-01T08:00:00.000+0000", "connected"))
        val a = LogBackupEngine.encode(events, pass)
        val b = LogBackupEngine.encode(events, pass)
        assertTrue(!a.contentEquals(b))
        // …yet both decrypt to the same payload.
        assertEquals(events, LogBackupEngine.decode(a, pass).eventLines)
        assertEquals(events, LogBackupEngine.decode(b, pass).eventLines)
    }

    // ------------------------------------------------------------------
    // Rejection paths
    // ------------------------------------------------------------------

    @Test(expected = LogBackupEngine.BackupException::class)
    fun `wrong passphrase is rejected`() {
        val blob = LogBackupEngine.encode(
            listOf(line("2026-10-01T08:00:00.000+0000", "connected")), pass
        )
        LogBackupEngine.decode(blob, wrong)
    }

    @Test(expected = LogBackupEngine.BackupException::class)
    fun `tampered ciphertext is rejected`() {
        val blob = LogBackupEngine.encode(
            listOf(line("2026-10-01T08:00:00.000+0000", "connected")), pass
        ).also { it[it.size - 3] = (it[it.size - 3].toInt() xor 0x41).toByte() }
        LogBackupEngine.decode(blob, pass)
    }

    @Test(expected = LogBackupEngine.BackupException::class)
    fun `wrong magic is rejected`() {
        val blob = LogBackupEngine.encode(
            listOf(line("2026-10-01T08:00:00.000+0000", "connected")), pass
        )
        blob[0] = 'X'.code.toByte()
        LogBackupEngine.decode(blob, pass)
    }

    @Test(expected = LogBackupEngine.BackupException::class)
    fun `truncated file is rejected`() {
        val blob = LogBackupEngine.encode(
            listOf(line("2026-10-01T08:00:00.000+0000", "connected")), pass
        )
        LogBackupEngine.decode(blob.copyOfRange(0, 10), pass)
    }

    // ------------------------------------------------------------------
    // Merge
    // ------------------------------------------------------------------

    @Test
    fun `merge dedupes identical lines and reports counts`() {
        val existing = listOf(
            line("2026-10-01T08:00:00.000+0000", "connected", "de-1"),
            line("2026-10-01T09:00:00.000+0000", "disconnected")
        )
        val imported = listOf(
            existing[0],
            line("2026-10-02T10:00:00.000+0000", "connected", "nl-2")
        )
        val result = LogBackupEngine.merge(existing, imported)

        assertEquals(3, result.mergedLines.size)
        assertEquals(1, result.added)
        assertEquals(1, result.duplicates)
    }

    @Test
    fun `merge orders events by timestamp`() {
        val a = line("2026-10-03T08:00:00.000+0000", "connected", "de-1")
        val b = line("2026-10-01T08:00:00.000+0000", "connected", "nl-2")
        val c = line("2026-10-02T08:00:00.000+0000", "disconnected")
        val result = LogBackupEngine.merge(listOf(a), listOf(b, c))

        assertEquals(listOf(b, c, a), result.mergedLines)
    }

    @Test
    fun `merge never drops existing events`() {
        val existing = listOf(line("2026-10-01T08:00:00.000+0000", "connected", "de-1"))
        val result = LogBackupEngine.merge(existing, emptyList())

        assertEquals(existing, result.mergedLines)
        assertEquals(0, result.added)
        assertEquals(0, result.duplicates)
    }

    @Test
    fun `merge skips blank lines`() {
        val result = LogBackupEngine.merge(
            listOf("  "), listOf("", "   ", line("2026-10-01T08:00:00.000+0000", "connected"))
        )
        assertEquals(1, result.mergedLines.size)
        assertEquals(1, result.added)
    }

    @Test
    fun `lines without a parseable timestamp sort last`() {
        val good = line("2026-10-01T08:00:00.000+0000", "connected", "de-1")
        val broken = "{\"event\":\"orphan\"}"
        val result = LogBackupEngine.merge(listOf(broken), listOf(good))

        assertEquals(listOf(good, broken), result.mergedLines)
    }
}
