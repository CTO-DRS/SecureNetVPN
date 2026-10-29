package com.securenet.vpn

import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideDifficulty
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
    fun `entry ids are whitespace-free store-key safe`() {
        // v1.8.1 — ids become SharedPreferences key suffixes (rating_<id>,
        // fav_<id>, note_<id>); whitespace inside would make debugging and
        // export ambiguous.
        WireGuideCatalog.entries.forEach { entry ->
            assertTrue(
                "Id with whitespace: '${entry.id}'",
                entry.id.isNotBlank() && entry.id.none { it.isWhitespace() }
            )
        }
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
            "Guide should list at least 58 sources, found ${WireGuideCatalog.entries.size}",
            WireGuideCatalog.entries.size >= 58
        )
        assertTrue(
            "Free section should list at least 4 sources",
            WireGuideCatalog.byCategory(GuideCategory.FREE).size >= 4
        )
        assertTrue(
            "Paid section should cover at least 20 providers",
            WireGuideCatalog.byCategory(GuideCategory.PAID).size >= 20
        )
        assertTrue(
            "Self-hosted section should list at least 16 tools",
            WireGuideCatalog.byCategory(GuideCategory.SELF_HOSTED).size >= 16
        )
        assertTrue(
            "Routers section should list at least 11 platforms",
            WireGuideCatalog.byCategory(GuideCategory.ROUTERS).size >= 11
        )
        assertTrue(
            "Apps section should list at least 7 clients",
            WireGuideCatalog.byCategory(GuideCategory.APPS).size >= 7
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
    fun `v1_8_4 expanded sources are present and verified`() {
        val byId = WireGuideCatalog.entries.associateBy { it.id }
        // RiseupVPN: free donation-funded service, community tool with honest note
        assertEquals(
            "https://github.com/kmille/riseup-vpn-configurator",
            byId.getValue("riseup").url
        )
        assertEquals(R.string.guide_note_riseup, byId.getValue("riseup").noteRes)
        // PureVPN: official member-area WireGuard add-on, disclosed via note
        assertEquals(
            "https://support.purevpn.com/en_US/account/how-to-add-wireguard-via-member-area",
            byId.getValue("purevpn").url
        )
        assertEquals(R.string.guide_note_purevpn, byId.getValue("purevpn").noteRes)
        // Self-hosted anchors (GitHub repos verified at release time)
        assertEquals("https://github.com/h44z/wg-portal", byId.getValue("wg-portal").url)
        assertEquals("https://github.com/tonarino/innernet", byId.getValue("innernet").url)
        assertEquals("https://defguard.net", byId.getValue("defguard").url)
        // Router anchors
        assertEquals("https://www.asuswrt-merlin.net", byId.getValue("asuswrt-merlin").url)
        assertEquals(
            R.string.guide_note_asuswrt_merlin,
            byId.getValue("asuswrt-merlin").noteRes
        )
        assertEquals("https://dd-wrt.com", byId.getValue("ddwrt").url)
        assertEquals("https://vyos.io", byId.getValue("vyos").url)
    }

    @Test
    fun `v1_8_5 expanded sources are present and verified`() {
        val byId = WireGuideCatalog.entries.associateBy { it.id }
        // Paid: three more generators verified against official pages
        assertEquals("https://12vpx.com", byId.getValue("12vpx").url)
        assertEquals("https://njal.la/vpn", byId.getValue("njalla").url)
        assertEquals(R.string.guide_note_njalla, byId.getValue("njalla").noteRes)
        assertEquals("https://cryptostorm.is/wireguard", byId.getValue("cryptostorm").url)
        // Self-hosted: obfuscation-focused Amnezia + Pro Custodibus management
        assertEquals(
            "https://amnezia.org/self-hosted",
            byId.getValue("amnezia").url
        )
        assertEquals(
            "https://www.procustodibus.com",
            byId.getValue("procustodibus").url
        )
        // Routers: Ubiquiti official help article + FreshTomato wiki with honest note
        assertEquals(
            "https://help.ui.com/hc/en-us/articles/115005445768-UniFi-Gateway-WireGuard-VPN-Server",
            byId.getValue("unifi").url
        )
        assertEquals(
            "https://wiki.freshtomato.org/doku.php/vpn-wireguard",
            byId.getValue("freshtomato").url
        )
        assertEquals(
            R.string.guide_note_freshtomato,
            byId.getValue("freshtomato").noteRes
        )
        // Apps: third-party Windows client with honest licensing note
        assertEquals("https://wiresock.net", byId.getValue("wiresock").url)
        assertEquals(R.string.guide_note_wiresock, byId.getValue("wiresock").noteRes)
    }

    @Test
    fun `v1_8_6 expanded sources are present and verified`() {
        val byId = WireGuideCatalog.entries.associateBy { it.id }
        // Free: VPNBook — public no-signup configs with honest 7-day expiry note
        assertEquals(
            "https://www.vpnbook.com/freevpn/wireguard-vpn",
            byId.getValue("vpnbook").url
        )
        assertEquals(R.string.guide_note_vpnbook, byId.getValue("vpnbook").noteRes)
        // Self-hosted: LinuxServer image, WireHole stack, Pritunl with client-auth note
        assertEquals(
            "https://docs.linuxserver.io/images/docker-wireguard/",
            byId.getValue("linuxserver-wireguard").url
        )
        assertEquals("https://github.com/IAmStoxe/wirehole", byId.getValue("wirehole").url)
        assertEquals(
            "https://docs.pritunl.com/kb/vpn/getting-started/wireguard",
            byId.getValue("pritunl").url
        )
        assertEquals(R.string.guide_note_pritunl, byId.getValue("pritunl").noteRes)
        // Routers: pfSense + IPFire official docs, ASUS stock firmware with model note
        assertEquals(
            "https://docs.netgate.com/pfsense/en/latest/vpn/wireguard/index.html",
            byId.getValue("pfsense").url
        )
        assertEquals(
            "https://www.ipfire.org/docs/configuration/services/wireguard",
            byId.getValue("ipfire").url
        )
        assertEquals(
            "https://www.asus.com/support/faq/1048280",
            byId.getValue("asus-stock").url
        )
        assertEquals(R.string.guide_note_asus_stock, byId.getValue("asus-stock").noteRes)
        // Apps: NetworkManager via Red Hat's official RHEL 9 WireGuard chapter
        assertEquals(
            "https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/9/html/configuring_and_managing_networking/assembly_setting-up-a-wireguard-vpn_configuring-and-managing-networking",
            byId.getValue("networkmanager").url
        )
        // Every new id carries EN+AR search keywords
        listOf(
            "vpnbook", "linuxserver-wireguard", "wirehole", "pritunl",
            "pfsense", "ipfire", "asus-stock", "networkmanager"
        ).forEach { id ->
            assertTrue("No search keywords for $id", WireGuideCatalog.keywordsOf(id).isNotEmpty())
        }
    }

    @Test
    fun `v1_8_12 expanded sources are present and verified`() {
        val byId = WireGuideCatalog.entries.associateBy { it.id }
        // Self-hosted, free software: dashboard + configuration-as-code
        assertEquals(
            "https://github.com/donaldzou/WGDashboard",
            byId.getValue("wgdashboard").url
        )
        assertEquals(
            "https://github.com/githubixx/ansible-role-wireguard",
            byId.getValue("ansible-wireguard").url
        )
        // Self-hosted hosting: permanent free tier vs paid VPS
        assertEquals(
            "https://www.oracle.com/cloud/free/",
            byId.getValue("oracle-alwaysfree").url
        )
        assertEquals(R.string.guide_note_oracle_free, byId.getValue("oracle-alwaysfree").noteRes)
        assertEquals("https://www.hetzner.com/cloud", byId.getValue("hetzner-cloud").url)
        assertEquals(R.string.guide_note_hetzner, byId.getValue("hetzner-cloud").noteRes)
        // Routers & firewalls: free OS vs paid appliances
        assertEquals("https://man.openbsd.org/wg", byId.getValue("openbsd-wg").url)
        assertEquals("https://firewalla.com", byId.getValue("firewalla").url)
        assertEquals(R.string.guide_note_firewalla, byId.getValue("firewalla").noteRes)
        assertEquals("https://www.netgate.com/appliances", byId.getValue("netgate-appliance").url)
        assertEquals(R.string.guide_note_netgate, byId.getValue("netgate-appliance").noteRes)
        assertEquals("https://keenetic.com", byId.getValue("keenetic").url)
        assertEquals(R.string.guide_note_keenetic, byId.getValue("keenetic").noteRes)
        assertEquals("https://omnia.turris.cz/en/", byId.getValue("turris-omnia").url)
        // Every new id carries EN+AR search keywords
        listOf(
            "wgdashboard", "ansible-wireguard", "oracle-alwaysfree", "hetzner-cloud",
            "openbsd-wg", "firewalla", "netgate-appliance", "keenetic", "turris-omnia"
        ).forEach { id ->
            assertTrue("No search keywords for $id", WireGuideCatalog.keywordsOf(id).isNotEmpty())
        }
        // The v1.8.12 promise: both sections now cover free AND paid options,
        // discoverable through the same search keywords (free/مجاني, paid/مدفوع).
        val freeKeys = setOf("free", "مجاني")
        val paidKeys = setOf("paid", "مدفوع")
        listOf(GuideCategory.SELF_HOSTED, GuideCategory.ROUTERS).forEach { category ->
            val ids = WireGuideCatalog.byCategory(category).map { it.id }
            assertTrue(
                "$category must cover free options",
                ids.any { WireGuideCatalog.keywordsOf(it).intersect(freeKeys).isNotEmpty() }
            )
            assertTrue(
                "$category must cover paid options",
                ids.any { WireGuideCatalog.keywordsOf(it).intersect(paidKeys).isNotEmpty() }
            )
        }
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

    @Test
    fun `editors choice ids exist and stay a bounded shortlist`() {
        val ids = WireGuideCatalog.entries.map { it.id }.toSet()
        val editors = WireGuideCatalog.editorsChoice
        assertTrue(
            "Badge set should be a curated shortlist (6..12), found ${editors.size}",
            editors.size in 6..12
        )
        assertEquals("Badge set must not duplicate ids", editors.size, editors.toSet().size)
        editors.forEach { id ->
            assertTrue("Unknown editors-choice id: $id", id in ids)
        }
        // v1.8.2 — isEditorsChoice mirrors the set for the UI + filter
        assertEquals(
            editors,
            WireGuideCatalog.entries.map { it.id }.filter { WireGuideCatalog.isEditorsChoice(it) }.toSet()
        )
    }

    // ------------------------------------------------------------------
    // v1.8.13 — the dedicated mesh section
    // ------------------------------------------------------------------

    @Test
    fun `v1_8_13 mesh section carries exactly the mesh coordination tools`() {
        // literal anchors: the five sources that moved out of self-hosted
        assertEquals(
            setOf("headscale", "tailscale", "netbird", "netmaker", "firezone"),
            WireGuideCatalog.byCategory(GuideCategory.MESH).map { it.id }.toSet()
        )
        // every mesh entry is reachable, keyworded, and price-marked free —
        // all five are free software / free personal or community tiers
        val freeKeys = setOf("free", "مجاني")
        listOf("headscale", "tailscale", "netbird", "netmaker", "firezone")
            .forEach { id ->
                val entry = WireGuideCatalog.byId(id)
                    ?: throw AssertionError("missing mesh entry $id")
                val keywords = WireGuideCatalog.keywordsOf(id)
                assertTrue("$id must keep search keywords", keywords.isNotEmpty())
                assertTrue(
                    "$id must carry a mesh keyword",
                    keywords.intersect(setOf("mesh", "ميش")).isNotEmpty()
                )
                assertTrue(
                    "$id must carry a free price marker",
                    keywords.intersect(freeKeys).isNotEmpty()
                )
                assertTrue("$id must keep its official URL", entry.url.startsWith("https://"))
            }
        // literal URL anchors, verified against the providers at release time
        assertEquals(
            "https://github.com/juanfont/headscale",
            WireGuideCatalog.byId("headscale")?.url
        )
        assertEquals(
            "https://tailscale.com",
            WireGuideCatalog.byId("tailscale")?.url
        )
        assertEquals(
            "https://www.firezone.dev",
            WireGuideCatalog.byId("firezone")?.url
        )
        // the mesh section has its own steps array and section label
        assertTrue(WireGuideCatalog.stepsRes(GuideCategory.MESH) != 0)
    }

    @Test
    fun `v1_8_14 every source declares a curated setup difficulty`() {
        // literal anchors — the classes the release notes and the detail
        // pages promise: hosted coordination is beginner, ops projects are
        // advanced, docker/script helpers are intermediate
        val expected = mapOf(
            "tailscale" to GuideDifficulty.BEGINNER,
            "netbird" to GuideDifficulty.BEGINNER,
            "mullvad" to GuideDifficulty.BEGINNER,
            "vpnbook" to GuideDifficulty.BEGINNER,
            "wg-android" to GuideDifficulty.BEGINNER,
            "amnezia" to GuideDifficulty.BEGINNER,
            "glinet" to GuideDifficulty.BEGINNER,
            "firewalla" to GuideDifficulty.BEGINNER,
            "cloudflare-warp-wgcf" to GuideDifficulty.INTERMEDIATE,
            "riseup" to GuideDifficulty.INTERMEDIATE,
            "wg-easy" to GuideDifficulty.INTERMEDIATE,
            "pivpn" to GuideDifficulty.INTERMEDIATE,
            "wgdashboard" to GuideDifficulty.INTERMEDIATE,
            "firezone" to GuideDifficulty.INTERMEDIATE,
            "netgate-appliance" to GuideDifficulty.INTERMEDIATE,
            "keenetic" to GuideDifficulty.INTERMEDIATE,
            "headscale" to GuideDifficulty.ADVANCED,
            "netmaker" to GuideDifficulty.ADVANCED,
            "openwrt" to GuideDifficulty.ADVANCED,
            "oracle-alwaysfree" to GuideDifficulty.ADVANCED
        )
        expected.forEach { (id, difficulty) ->
            val entry = WireGuideCatalog.byId(id)
                ?: throw AssertionError("missing entry $id")
            assertEquals("wrong difficulty for $id", difficulty, entry.difficulty)
            // the localized-label mapping resolves for every class
            assertTrue(
                "difficultyLabelRes must resolve for $id",
                WireGuideCatalog.difficultyLabelRes(entry.difficulty) != 0
            )
        }

        // distribution rules: generator/client sections never say advanced,
        // self-hosted/router sections are never beginner except the three
        // genuinely one-app installs
        listOf(GuideCategory.FREE, GuideCategory.PAID, GuideCategory.APPS)
            .forEach { category ->
                WireGuideCatalog.byCategory(category).forEach { entry ->
                    assertTrue(
                        "${entry.id}: $category must not be ADVANCED",
                        entry.difficulty != GuideDifficulty.ADVANCED
                    )
                }
            }
        assertEquals(
            setOf("amnezia"),
            WireGuideCatalog.byCategory(GuideCategory.SELF_HOSTED)
                .filter { it.difficulty == GuideDifficulty.BEGINNER }
                .map { it.id }.toSet()
        )
        assertEquals(
            setOf("glinet", "firewalla"),
            WireGuideCatalog.byCategory(GuideCategory.ROUTERS)
                .filter { it.difficulty == GuideDifficulty.BEGINNER }
                .map { it.id }.toSet()
        )
        // every one of the 69 sources carries a class and a resolvable label
        assertEquals(69, WireGuideCatalog.entries.size)
        WireGuideCatalog.entries.forEach { entry ->
            assertTrue(
                "${entry.id} must declare a difficulty",
                WireGuideCatalog.difficultyLabelRes(entry.difficulty) != 0
            )
        }
    }
}
