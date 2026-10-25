package com.securenet.vpn

import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.WireGuideCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * v1.7 — integrity tests for the in-app "Where to get WireGuard configs"
 * catalog. The guide is static content shipped to users; a typo in a URL or
 * a missing section would ship silently without these checks.
 */
class WireGuideCatalogTest {

    @Test
    fun `every entry has a well-formed https url`() {
        WireGuideCatalog.entries.forEach { entry ->
            val uri = runCatching { URI(entry.url) }.getOrNull()
            assertTrue("Unparseable URL in ${entry.id}: ${entry.url}", uri != null)
            assertEquals("Non-https URL in ${entry.id}", "https", uri!!.scheme)
            assertTrue("Missing host in ${entry.id}", uri.host?.isNotBlank() == true)
        }
    }

    @Test
    fun `entry ids are unique`() {
        val ids = WireGuideCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `entry names are non-blank`() {
        WireGuideCatalog.entries.forEach { entry ->
            assertTrue("Blank name in ${entry.id}", entry.name.isNotBlank())
        }
    }

    @Test
    fun `all four categories are populated`() {
        GuideCategory.entries.forEach { category ->
            val count = WireGuideCatalog.byCategory(category).size
            assertTrue("Category $category has no entries", count > 0)
        }
    }

    @Test
    fun `guide covers the expected breadth`() {
        assertTrue(
            "Guide should list at least 20 sources, found ${WireGuideCatalog.entries.size}",
            WireGuideCatalog.entries.size >= 20
        )
        assertTrue(
            "Paid section should cover at least 10 providers",
            WireGuideCatalog.byCategory(GuideCategory.PAID).size >= 10
        )
        assertTrue(
            "Self-hosted section should list at least 3 tools",
            WireGuideCatalog.byCategory(GuideCategory.SELF_HOSTED).size >= 3
        )
    }

    @Test
    fun `key verified sources are present`() {
        val byId = WireGuideCatalog.entries.associateBy { it.id }
        // Free tier anchors
        assertEquals(
            "https://github.com/ViRb3/wgcf",
            byId.getValue("cloudflare-warp-wgcf").url
        )
        assertEquals(
            "https://account.protonvpn.com/downloads",
            byId.getValue("proton-free").url
        )
        // Paid generators verified against provider documentation
        assertEquals(
            "https://mullvad.net/en/account/wireguard-config",
            byId.getValue("mullvad").url
        )
        assertEquals(
            "https://windscribe.com/getconfig/wireguard",
            byId.getValue("windscribe").url
        )
        // The Windscribe note must disclose the paid-plan requirement
        assertEquals(
            R.string.guide_note_paid_only,
            byId.getValue("windscribe").noteRes
        )
        // Official client anchor
        assertTrue(byId.containsKey("wg-android"))
    }

    @Test
    fun `sections return all categories in enum order`() {
        val sections = WireGuideCatalog.sections()
        assertEquals(GuideCategory.entries, sections.map { it.first })
        assertEquals(WireGuideCatalog.entries.size, sections.sumOf { it.second.size })
    }
}
