package com.securenet.vpn

import com.securenet.vpn.guide.GuideSeenCatalog
import com.securenet.vpn.guide.InMemoryGuideSeenCatalog
import com.securenet.vpn.guide.WireGuideCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.16 — the pure rules behind the fresh-sources alert (تنبيه عند إضافة
 * مصادر جديدة). The banner paints whatever [GuideSeenCatalog.unseen]
 * returns, so the first-run baseline must stay silent, a partially-seen
 * catalog must surface only the genuine arrivals in catalog order, and
 * stale seen ids (sources later removed) must degrade to "already seen"
 * instead of resurrecting ghosts.
 */
class GuideSeenCatalogTest {

    private val currentIds = listOf("alpha", "beta", "gamma", "delta")

    @Test
    fun `first run with empty seen stays silent`() {
        // baseline: everything gets recorded silently, nothing to announce
        assertEquals(emptyList<String>(), GuideSeenCatalog.unseen(currentIds, emptySet()))
    }

    @Test
    fun `unseen returns only the genuine arrivals in catalog order`() {
        val unseen = GuideSeenCatalog.unseen(currentIds, setOf("beta"))
        assertEquals(listOf("alpha", "gamma", "delta"), unseen)
    }

    @Test
    fun `fully seen catalog and stale ids both degrade gracefully`() {
        assertEquals(
            emptyList<String>(),
            GuideSeenCatalog.unseen(currentIds, currentIds.toSet())
        )
        // ids that left the catalog never resurrect as "new" — and the
        // genuinely-acknowledged alpha stays acknowledged
        assertEquals(
            listOf("beta", "gamma", "delta"),
            GuideSeenCatalog.unseen(currentIds, setOf("alpha", "ghost-1", "ghost-2"))
        )
    }

    @Test
    fun `namesOf maps ids to display names in catalog order`() {
        val first = WireGuideCatalog.entries[0]
        val second = WireGuideCatalog.entries[1]
        assertTrue(WireGuideCatalog.entries.size >= 2)
        assertEquals(
            listOf(first.name, second.name),
            GuideSeenCatalog.namesOf(
                WireGuideCatalog.entries, listOf(second.id, first.id)
            )
        )
    }

    @Test
    fun `in-memory store merges acknowledgements`() {
        val store = InMemoryGuideSeenCatalog()
        assertEquals(emptySet<String>(), store.seenIds())
        store.markAllSeen(listOf("alpha"))
        assertEquals(setOf("alpha"), store.seenIds())
        store.markAllSeen(listOf("beta", "gamma"))
        assertEquals(setOf("alpha", "beta", "gamma"), store.seenIds())
        // re-acknowledging the same ids is a no-op, not a duplicate
        store.markAllSeen(listOf("alpha"))
        assertEquals(setOf("alpha", "beta", "gamma"), store.seenIds())
    }

    @Test
    fun `unseen drives the real catalog end to end`() {
        val store = InMemoryGuideSeenCatalog()
        val allIds = WireGuideCatalog.entries.map { it.id }
        // simulate a fresh install on the current catalog: baseline silently
        store.markAllSeen(allIds)
        // simulate a future catalog that adds two sources at the front
        val futureIds = listOf("brand-new-1", "brand-new-2") + allIds
        val unseen = GuideSeenCatalog.unseen(futureIds, store.seenIds())
        assertEquals(listOf("brand-new-1", "brand-new-2"), unseen)
        assertTrue(unseen.all { it !in allIds })
    }
}
