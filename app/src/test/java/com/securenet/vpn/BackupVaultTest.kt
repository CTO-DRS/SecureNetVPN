package com.securenet.vpn

import com.securenet.vpn.backup.BackupVault
import com.securenet.vpn.backup.LogBackupEngine
import com.securenet.vpn.guide.GuideSignalsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * v1.8.3 — the in-app backup vault: multiple encrypted `.snback` slots with
 * non-sensitive `.meta` sidecars in a private directory. Covered here:
 * create/list/read/delete round-trips (guide signals included), wrong
 * passphrase rejection, corrupt-sidecar and orphaned-payload tolerance,
 * the MAX_SLOTS prune, name hygiene and the slot-id safety rules.
 */
class BackupVaultTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var vault: BackupVault
    private val pass = "vault pass phrase".toCharArray()
    private val wrong = "wrong pass phrase".toCharArray()

    @Before
    fun setUp() {
        vault = BackupVault(tmp.newFolder("vault"))
    }

    private fun event(ts: String, event: String) =
        "{\"ts\":\"$ts\",\"event\":\"$event\"}"

    private val signals = GuideSignalsData(
        ratings = mapOf("mullvad" to 5, "ivpn" to 4),
        favorites = setOf("mullvad"),
        notes = mapOf("ivpn" to "fast at night")
    )

    @Test
    fun `create and list expose the slot metadata`() {
        val meta = vault.create(
            "قبل الجذر", listOf(event("2026-10-26T08:00:00.000+0000", "connected")),
            pass, signals, nowMillis = 1_770_000_000_000
        )
        val slots = vault.list()
        assertEquals(1, slots.size)
        val slot = slots.single()
        assertEquals(meta.id, slot.id)
        assertEquals("قبل الجذر", slot.name)
        assertEquals(1_770_000_000_000, slot.createdAtMillis)
        assertEquals(1, slot.eventCount)
        assertEquals(4, slot.signalCount) // 2 ratings + 1 favorite + 1 note
        assertTrue(slot.sizeBytes > 0)
        assertTrue(vault.hasSlot(meta.id))
        assertTrue(File(tmp.root, "vault/${slot.id}.snback").isFile)
        assertTrue(File(tmp.root, "vault/${slot.id}.meta").isFile)
    }

    @Test
    fun `read decrypts the same events and signals back`() {
        val lines = listOf(
            event("2026-10-26T08:00:00.000+0000", "connected"),
            event("2026-10-26T09:00:00.000+0000", "disconnected")
        )
        val meta = vault.create("slot", lines, pass, signals)
        val payload = vault.read(meta.id, pass)
        assertEquals(lines, payload.eventLines)
        assertEquals(signals, payload.signals)
        assertTrue(payload.createdAtMillis > 0)
    }

    @Test
    fun `wrong passphrase is rejected`() {
        val meta = vault.create("slot", listOf(event("2026-10-26T08:00:00.000+0000", "x")), pass)
        try {
            vault.read(meta.id, wrong)
            throw AssertionError("expected BackupException")
        } catch (e: LogBackupEngine.BackupException) {
            // expected
        }
    }

    @Test
    fun `delete removes payload and sidecar`() {
        val meta = vault.create("slot", emptyList(), pass)
        assertTrue(vault.delete(meta.id))
        assertFalse(vault.hasSlot(meta.id))
        assertTrue(vault.list().isEmpty())
        assertFalse(vault.delete(meta.id)) // second delete is a no-op
    }

    @Test
    fun `list skips corrupt sidecars and orphaned payloads`() {
        val healthy = vault.create("healthy", listOf(event("2026-10-26T08:00:00.000+0000", "x")), pass)
        val corruptMeta = vault.create("corrupt meta", emptyList(), pass)
        val orphanPayload = vault.create("orphan", emptyList(), pass)

        File(tmp.root, "vault/${corruptMeta.id}.meta").writeText("{ not json")
        File(tmp.root, "vault/${orphanPayload.id}.snback").delete()

        val slots = vault.list()
        assertEquals(listOf(healthy.id), slots.map { it.id })
    }

    @Test
    fun `list orders newest first`() {
        val old = vault.create("old", emptyList(), pass, nowMillis = 1_000_000)
        val mid = vault.create("mid", emptyList(), pass, nowMillis = 2_000_000)
        val new = vault.create("new", emptyList(), pass, nowMillis = 3_000_000)
        assertEquals(listOf(new.id, mid.id, old.id), vault.list().map { it.id })
    }

    @Test
    fun `prune keeps only the newest MAX_SLOTS`() {
        val total = BackupVault.MAX_SLOTS + 2
        var stamp = 1_000_000L
        val ids = (1..total).map {
            stamp += 1_000_000
            vault.create("slot $it", emptyList(), pass, nowMillis = stamp).id
        }
        val slots = vault.list()
        assertEquals(BackupVault.MAX_SLOTS, slots.size)
        // the two oldest slots are gone
        assertFalse(ids[0] in slots.map { it.id })
        assertFalse(ids[1] in slots.map { it.id })
        assertTrue(ids.last() in slots.map { it.id })
    }

    @Test
    fun `names are sanitised for display`() {
        assertEquals(
            "my backup name",
            vault.sanitizeName("  my\nbackup \t name \u0007 ")
        )
        assertEquals(BackupVault.MAX_NAME_LEN, vault.sanitizeName("x".repeat(200)).length)
        assertEquals(BackupVault.DEFAULT_NAME, vault.sanitizeName("   "))
        // Arabic and quotes survive sanitisation (escaping happens in the codec)
        assertEquals("قبل \"الجذر\"", vault.sanitizeName(" قبل \"الجذر\" "))
    }

    @Test
    fun `meta codec escapes and parses special characters`() {
        val meta = vault.create("name \"with\" quotes", emptyList(), pass)
        val listed = vault.list().single()
        assertEquals("name \"with\" quotes", listed.name)
        assertEquals(meta.id, listed.id)

        // raw sidecar JSON must stay single-line even with a quoted name
        val sidecar = File(tmp.root, "vault/${meta.id}.meta").readText()
        assertFalse(sidecar.contains('\n'))
        assertTrue(sidecar.startsWith("{\"v\":1"))
    }

    @Test
    fun `decodeMeta tolerates malformed and hostile input`() {
        assertNull(vault.decodeMeta("not json"))
        assertNull(vault.decodeMeta("{}")) // no id
        assertNull(vault.decodeMeta("{\"v\":1}"))
        // path-traversal ids are rejected outright
        assertNull(vault.decodeMeta(
            "{\"v\":1,\"id\":\"..\",\"name\":\"n\",\"created\":1,\"events\":0,\"signals\":0}"
        ))
        assertNull(vault.decodeMeta(
            "{\"v\":1,\"id\":\"a/b\",\"name\":\"n\",\"created\":1,\"events\":0,\"signals\":0}"
        ))
        // trailing garbage rejected
        assertNull(vault.decodeMeta(
            "{\"v\":1,\"id\":\"ok\",\"name\":\"n\",\"created\":1,\"events\":0,\"signals\":0} junk"
        ))
        // unknown members are tolerated (forward compatibility)
        val decoded = vault.decodeMeta(
            "{\"v\":1,\"id\":\"ok\",\"name\":\"n\",\"created\":7,\"events\":2,\"signals\":3,\"future\":true}"
        )
        assertEquals("ok", decoded?.id)
        assertEquals(7L, decoded?.createdAtMillis)
        assertEquals(2, decoded?.eventCount)
        assertEquals(3, decoded?.signalCount)
    }

    @Test
    fun `read and delete reject path traversal ids`() {
        try {
            vault.read("../evil", pass)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        assertFalse(vault.delete("../evil"))
        assertFalse(vault.hasSlot("../evil"))
    }

    @Test
    fun `read of a missing slot raises a BackupException`() {
        try {
            vault.read("20990101_000000-dead", pass)
            throw AssertionError("expected BackupException")
        } catch (e: LogBackupEngine.BackupException) {
            // expected
        }
    }
}
