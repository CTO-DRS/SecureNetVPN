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
    fun `entry urls are unique`() {
        val urls = WireGuideCatalog.entries.map { it.url }
        assertEquals(urls.size, urls.toSet().size)
    }

    @Test
    fun `all five categories are populated`() {
        GuideCategory.entries.forEach { category ->
            val count = WireGuideCatalog.byCategory(category).size
            assertTrue("Category $category has no entries", count > 0)
        }
    }

    @Test
    fun `guide covers the expected breadth`() {
        assertTrue(
            "Guide should list at least 34 sources, found ${WireGuideCatalog.entries.size}",
            WireGuideCatalog.entries.size >= 34
        )
        assertTrue(
            "Paid section should cover at least 16 providers",
            WireGuideCatalog.byCategory(GuideCategory.PAID).size >= 16
        )
        assertTrue(
            "Self-hosted section should list at least 8 tools",
            WireGuideCatalog.byCategory(GuideCategory.SELF_HOSTED).size >= 8
        )
        assertTrue(
            "Routers section should list at least 4 platforms",
            WireGuideCatalog.byCategory(GuideCategory.ROUTERS).size >= 4
        )
        assertTrue(
            "Apps section should list at least 5 clients",
            WireGuideCatalog.byCategory(GuideCategory.APPS).size >= 5
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
    fun `v1_7_1 expanded sources are present and verified`() {
        val byId = WireGuideCatalog.entries.associateBy { it.id }
        // TorGuard: generator verified in the member's area (support docs)
        assertEquals("https://torguard.net", byId.getValue("torguard").url)
        // PIA: honest note required — no official generator, community tool linked
        assertEquals(
            "https://github.com/kylegrantlucas/pia-wg-config",
            byId.getValue("pia").url
        )
        assertEquals(R.string.guide_note_pia, byId.getValue("pia").noteRes)
        // NordVPN: entry must disclose the missing official .conf export
        assertEquals(R.string.guide_note_nordvpn, byId.getValue("nordvpn").noteRes)
        // Router anchors
        assertEquals("https://www.gl-inet.com", byId.getValue("glinet").url)
        assertEquals("https://openwrt.org", byId.getValue("openwrt").url)
        assertEquals("https://opnsense.org", byId.getValue("opnsense").url)
        assertEquals("https://mikrotik.com", byId.getValue("mikrotik").url)
        // Mesh/self-hosted anchors
        assertEquals("https://tailscale.com", byId.getValue("tailscale").url)
        assertEquals(
            "https://github.com/angristan/wireguard-install",
            byId.getValue("wireguard-install").url
        )
        // Official Apple clients — App Store host, known listing IDs
        assertEquals(
            "https://apps.apple.com/app/wireguard/id1441195209",
            byId.getValue("wg-ios").url
        )
        assertEquals(
            "https://apps.apple.com/app/wireguard/id1451685025",
            byId.getValue("wg-macos").url
        )
    }

    @Test
    fun `every category maps to a distinct steps resource`() {
        val stepRes = GuideCategory.entries.map { WireGuideCatalog.stepsRes(it) }
        assertTrue(stepRes.none { it == 0 })
        assertEquals(stepRes.size, stepRes.toSet().size)
    }

    @Test
    fun `sections return all categories in enum order`() {
        val sections = WireGuideCatalog.sections()
        assertEquals(GuideCategory.entries, sections.map { it.first })
        assertEquals(WireGuideCatalog.entries.size, sections.sumOf { it.second.size })
    }
}
