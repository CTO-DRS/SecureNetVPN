package com.securenet.vpn

import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.WireGuideCatalog
import com.securenet.vpn.guide.WireGuideFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8 — search/filter logic for the config-sources guide. The catalog is
 * static shipped content; a broken filter (e.g. one that hides everything,
 * or ignores Arabic tokens) would silently degrade the feature.
 */
class WireGuideFilterTest {

    private val entries = WireGuideCatalog.entries

    @Test
    fun `blank query matches every entry`() {
        assertEquals(entries.size, WireGuideFilter.filter(entries, "   ").size)
    }

    @Test
    fun `name tokens match the right entry`() {
        val hits = WireGuideFilter.filter(entries, "mullvad")
        assertEquals(listOf("mullvad"), hits.map { it.id })
    }

    @Test
    fun `english keyword matches aliases`() {
        // "warp" is a curated keyword of the Cloudflare entry
        val hits = WireGuideFilter.filter(entries, "warp")
        assertTrue(hits.map { it.id }.contains("cloudflare-warp-wgcf"))
        assertTrue(hits.all { it.category == GuideCategory.FREE })
    }

    @Test
    fun `arabic keyword matches — كلاودفلير`() {
        val hits = WireGuideFilter.filter(entries, "كلاودفلير")
        assertEquals(listOf("cloudflare-warp-wgcf"), hits.map { it.id })
    }

    @Test
    fun `arabic category terms select whole categories`() {
        val free = WireGuideFilter.filter(entries, "مجاني")
        assertTrue(free.isNotEmpty())
        assertTrue(free.all { it.category == GuideCategory.FREE })

        val paid = WireGuideFilter.filter(entries, "مدفوع")
        assertTrue(paid.isNotEmpty())
        assertTrue(paid.all { it.category == GuideCategory.PAID })
    }

    @Test
    fun `multi token query requires every token`() {
        // pivpn is the only entry whose haystack holds both tokens
        val hits = WireGuideFilter.filter(entries, "raspberry pi")
        assertEquals(listOf("pivpn"), hits.map { it.id })

        // tokens spanning different entries must not AND together
        assertTrue(WireGuideFilter.filter(entries, "raspberry nordvpn").isEmpty())
    }

    @Test
    fun `category filter restricts results and composes with query`() {
        val paidCount = WireGuideCatalog.byCategory(GuideCategory.PAID).size
        assertEquals(
            paidCount,
            WireGuideFilter.filter(entries, "", GuideCategory.PAID).size
        )
        // query inside a restricted category
        val nord = WireGuideFilter.filter(entries, "nord", GuideCategory.PAID)
        assertEquals(listOf("nordvpn"), nord.map { it.id })
        // query matches nothing in a restricted category → empty
        assertTrue(
            WireGuideFilter.filter(entries, "warp", GuideCategory.PAID).isEmpty()
        )
    }

    @Test
    fun `url fragments match`() {
        assertTrue(
            WireGuideFilter.filter(entries, "gl-inet").map { it.id }.contains("glinet")
        )
        assertTrue(
            WireGuideFilter.filter(entries, "apps.apple.com")
                .all { it.category == GuideCategory.APPS }
        )
    }

    @Test
    fun `every entry is reachable through its own keywords`() {
        entries.forEach { entry ->
            val keywords = WireGuideCatalog.keywordsOf(entry.id)
            assertTrue("No keywords for ${entry.id}", keywords.isNotEmpty())
            val first = keywords.first()
            assertTrue(
                "Keyword '$first' does not match ${entry.id}",
                WireGuideFilter.matches(entry, first)
            )
        }
    }

    @Test
    fun `match is case insensitive`() {
        val mullvad = WireGuideCatalog.entries.associateBy { it.id }.getValue("mullvad")
        assertTrue(WireGuideFilter.matches(mullvad, "MULLVAD"))
        assertFalse(
            WireGuideFilter.filter(entries, "zzznothing").isNotEmpty()
        )
    }
}
