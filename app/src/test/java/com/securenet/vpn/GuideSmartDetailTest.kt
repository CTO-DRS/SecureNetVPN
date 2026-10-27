package com.securenet.vpn

import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideEntry
import com.securenet.vpn.guide.GuideSmart
import com.securenet.vpn.guide.GuideSignalsData
import com.securenet.vpn.guide.WireGuideCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.10 — tests for the detail-page smart layer: the personalized
 * verdict under the identity card and the per-sibling reason chips in
 * "more in this category". Pure JVM over hand-built [GuideSignalsData]
 * snapshots, exactly like [GuideSmartTest] — no Android, no store.
 *
 * Anchored on literal catalog ids so a future catalog edit that changes
 * the underlying facts (keywords, Editor's-choice list, categories) fails
 * loudly instead of silently keeping stale expectations.
 */
class GuideSmartDetailTest {

    private val entries = WireGuideCatalog.entries
    private val byId = entries.associateBy { it.id }

    private fun signals(
        ratings: Map<String, Int> = emptyMap(),
        favorites: Set<String> = emptySet(),
        notes: Map<String, String> = emptyMap()
    ) = GuideSignalsData(ratings, favorites, notes)

    /** mullvad, windscribe, ivpn are all PAID; one signal each kind. */
    private val paidMixed = signals(
        ratings = mapOf("windscribe" to 4),
        favorites = setOf("mullvad"),
        notes = mapOf("ivpn" to "fast and private")
    )

    private fun entry(id: String): GuideEntry = byId.getValue(id)

    // ---------------- verdict ----------------

    @Test
    fun `cold start never produces a verdict`() {
        entries.forEach { entry ->
            assertNull("${entry.id} must stay neutral without signals",
                GuideSmart.verdict(entry, signals()))
        }
    }

    @Test
    fun `a source inside the most-engaged category gets a count-backed verdict`() {
        val v = GuideSmart.verdict(entry("mullvad"), paidMixed)
        assertEquals(GuideSmart.VerdictKind.TOP_CATEGORY, v?.kind)
        assertEquals(GuideCategory.PAID, v?.category)
        // favorites + ratings + notes all count: 1 + 1 + 1.
        assertEquals(3, v?.count)
    }

    @Test
    fun `unrated editors choice outside the top category gets the editors verdict`() {
        // tailscale is badged, unrated, and SELF_HOSTED — not the PAID top.
        val v = GuideSmart.verdict(entry("tailscale"), paidMixed)
        assertEquals(GuideSmart.VerdictKind.EDITORS_CHOICE, v?.kind)
        assertNull(v?.category)
        assertEquals(0, v?.count)
    }

    @Test
    fun `affinity ties resolve by the higher category ordinal`() {
        // mullvad (PAID) and tailscale (SELF_HOSTED) both carry 1 signal.
        // maxWithOrNull breaks the 1–1 tie with the ascending-ordinal
        // comparator, so SELF_HOSTED wins the top slot — the rated mullvad
        // then stays quiet (badged but rated, not in the top category).
        val s = signals(ratings = mapOf("mullvad" to 5, "tailscale" to 4))
        assertEquals(GuideSmart.VerdictKind.TOP_CATEGORY,
            GuideSmart.verdict(entry("tailscale"), s)?.kind)
        assertNull("a rated badge outside the top category stays quiet",
            GuideSmart.verdict(entry("mullvad"), s))
    }

    @Test
    fun `verdict count backs the top category with every signal kind`() {
        // All five categories get exactly one signal except FREE (two):
        // the verdict for cloudflare-warp-wgcf must count both.
        val s = signals(
            ratings = mapOf("vpnbook" to 3),          // FREE
            favorites = setOf("riseup"),              // FREE
            notes = mapOf("mullvad" to "note")        // PAID
        )
        val v = GuideSmart.verdict(entry("vpnbook"), s)
        assertEquals(GuideSmart.VerdictKind.TOP_CATEGORY, v?.kind)
        assertEquals(GuideCategory.FREE, v?.category)
        assertEquals(2, v?.count)
    }

    // ---------------- sibling reasons ----------------

    @Test
    fun `shared catalog keywords make siblings similar — symmetrically`() {
        // mullvad and azirevpn both carry the "sweden" keyword.
        val shared = GuideSmart.sharedKeywords(entry("mullvad"), entry("azirevpn"))
        assertEquals(setOf("sweden"), shared)
        assertEquals(GuideSmart.sharedKeywords(entry("azirevpn"), entry("mullvad")),
            shared)
        assertEquals(GuideSmart.Reason.SIMILAR,
            GuideSmart.siblingReason(entry("azirevpn"), entry("mullvad"), paidMixed))
        assertEquals(GuideSmart.Reason.SIMILAR,
            GuideSmart.siblingReason(entry("mullvad"), entry("azirevpn"), paidMixed))
    }

    @Test
    fun `similarity works across categories and without any signals`() {
        // tailscale and netbird share "mesh" in SELF_HOSTED; the chip does
        // not depend on user signals, so cold start still explains it.
        assertTrue(GuideSmart.sharedKeywords(entry("tailscale"), entry("netbird"))
            .isNotEmpty())
        assertEquals(GuideSmart.Reason.SIMILAR,
            GuideSmart.siblingReason(entry("netbird"), entry("tailscale"), signals()))
    }

    @Test
    fun `unrated editors choice sibling without overlap gets the trust nudge`() {
        // mullvad and ivpn share no keywords; mullvad is badged and unrated.
        assertTrue(GuideSmart.sharedKeywords(entry("mullvad"), entry("ivpn")).isEmpty())
        assertEquals(GuideSmart.Reason.EDITORS_CHOICE,
            GuideSmart.siblingReason(entry("mullvad"), entry("ivpn"), paidMixed))
    }

    @Test
    fun `a rated editors choice sibling without overlap gets no chip`() {
        val s = signals(ratings = mapOf("mullvad" to 5))
        assertNull(GuideSmart.siblingReason(entry("mullvad"), entry("ivpn"), s))
    }

    @Test
    fun `plain siblings without overlap stay silent`() {
        // windscribe and azirevpn: no shared keywords, neither carries the
        // Editor's-choice badge — nothing grounded to say about either.
        assertNull(GuideSmart.siblingReason(entry("windscribe"), entry("azirevpn"), paidMixed))
        assertNull(GuideSmart.siblingReason(entry("azirevpn"), entry("windscribe"), paidMixed))
    }

    @Test
    fun `self comparison never produces a reason`() {
        assertNull(GuideSmart.siblingReason(entry("mullvad"), entry("mullvad"), paidMixed))
    }

    // ---------------- catalog-wide sweeps ----------------

    @Test
    fun `every catalog sibling reason is grounded or silent`() {
        entries.forEach { current ->
            entries.filter { it.category == current.category && it.id != current.id }
                .forEach { sibling ->
                    val reason = GuideSmart.siblingReason(sibling, current, paidMixed)
                    val shared = GuideSmart.sharedKeywords(sibling, current)
                    when (reason) {
                        GuideSmart.Reason.SIMILAR ->
                            assertTrue(
                                "${sibling.id} vs ${current.id}: SIMILAR needs " +
                                    "shared keywords", shared.isNotEmpty())
                        GuideSmart.Reason.EDITORS_CHOICE -> {
                            assertTrue(
                                "${sibling.id}: trust nudge needs the badge",
                                WireGuideCatalog.isEditorsChoice(sibling.id))
                            assertTrue(
                                "${sibling.id}: trust nudge needs it unrated",
                                sibling.id !in paidMixed.ratings)
                        }
                        else -> assertTrue(
                            "${sibling.id} vs ${current.id}: silence must mean " +
                                "no shared keywords and no unrated badge",
                            shared.isEmpty() &&
                                (!WireGuideCatalog.isEditorsChoice(sibling.id) ||
                                    sibling.id in paidMixed.ratings))
                    }
                }
        }
    }

    @Test
    fun `every catalog verdict stays consistent with the affinity math`() {
        val affinity = GuideSmart.categoryAffinity(paidMixed, byId)
        val top = affinity.entries.maxWithOrNull(
            compareBy<Map.Entry<GuideCategory, Int>> { it.value }
                .thenBy { it.key.ordinal }
        )
        entries.forEach { entry ->
            val v = GuideSmart.verdict(entry, paidMixed)
            if (top != null && entry.category == top.key) {
                assertEquals("${entry.id} must carry the top-category verdict",
                    GuideSmart.VerdictKind.TOP_CATEGORY, v?.kind)
            } else if (WireGuideCatalog.isEditorsChoice(entry.id) &&
                entry.id !in paidMixed.ratings
            ) {
                assertEquals("${entry.id} must carry the editors verdict",
                    GuideSmart.VerdictKind.EDITORS_CHOICE, v?.kind)
            } else {
                assertNull("${entry.id} must stay silent", v)
            }
        }
    }
}
