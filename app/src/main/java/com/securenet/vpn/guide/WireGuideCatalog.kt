package com.securenet.vpn.guide

import androidx.annotation.StringRes
import com.securenet.vpn.R

/**
 * v1.7 — Catalog of sources for obtaining WireGuard profiles that import
 * straight into SecureNet (Servers tab → Import). Five categories cover the
 * full spectrum the user asked for: free services, paid providers with a
 * config generator, self-hosted server software, router/firewall platforms
 * and official clients/tools. Expanded in v1.7.1 from 23 to 36 sources,
 * adding per-category step-by-step tutorials ([stepsRes]), in v1.8.4 from
 * 36 to 44 (RiseupVPN community configs, PureVPN, WireGuard Portal /
 * innernet / defguard, Asuswrt-Merlin / DD-WRT / VyOS), in v1.8.5 to
 * 52 (12VPX / Njalla / Cryptostorm, Amnezia / Pro Custodibus, Ubiquiti
 * UniFi / FreshTomato, WireSock) and in v1.8.6 to 60 (VPNBook,
 * linuxserver/wireguard / WireHole / Pritunl, pfSense / IPFire / ASUS
 * stock firmware, NetworkManager). In v1.8.12 to 69 with explicit
 * free-vs-paid coverage of the self-hosted and router/firewall sections:
 * free — WGDashboard, the githubixx Ansible role, Oracle Cloud Always
 * Free, OpenBSD wg(4); paid — Hetzner Cloud VPS, Firewalla, Netgate
 * pfSense+ appliances, Keenetic, Turris Omnia. Free/paid markers live
 * in the search keywords (free/مجاني, paid/مدفوع) so filtering by
 * price works through the same search path.
 *
 * The catalog is pure data so it can be unit-tested (URL shape, uniqueness,
 * category completeness) without any Android dependency beyond @StringRes.
 * Descriptions live in strings.xml (EN) and values-ar/strings.xml (AR).
 *
 * URLs were verified against the providers' own documentation (dashboards,
 * knowledge bases and the GL.iNet/TP-Link provider guides) at release time.
 * v1.7.1 additions were verified the same way (TorGuard member-area generator,
 * PIA community tooling, Tailscale/GL.iNet official docs, App Store listings).
 */
enum class GuideCategory {
    FREE, PAID, SELF_HOSTED, ROUTERS, APPS
}

data class GuideEntry(
    val id: String,
    val name: String,
    val url: String,
    val category: GuideCategory,
    @StringRes val descriptionRes: Int,
    /** Optional small-print note shown under the description. */
    @StringRes val noteRes: Int? = null
)

object WireGuideCatalog {

    val entries: List<GuideEntry> = listOf(
        // ------------------------------------------------------------------
        // FREE — services that hand you a working .conf at no cost
        // ------------------------------------------------------------------
        GuideEntry(
            id = "cloudflare-warp-wgcf",
            name = "Cloudflare WARP + wgcf",
            url = "https://github.com/ViRb3/wgcf",
            category = GuideCategory.FREE,
            descriptionRes = R.string.guide_desc_warp_wgcf
        ),
        GuideEntry(
            id = "proton-free",
            name = "Proton VPN",
            url = "https://account.protonvpn.com/downloads",
            category = GuideCategory.FREE,
            descriptionRes = R.string.guide_desc_proton_free
        ),
        GuideEntry(
            id = "riseup",
            name = "RiseupVPN (community configs)",
            url = "https://github.com/kmille/riseup-vpn-configurator",
            category = GuideCategory.FREE,
            descriptionRes = R.string.guide_desc_riseup,
            noteRes = R.string.guide_note_riseup
        ),
        GuideEntry(
            id = "vpnbook",
            name = "VPNBook (free public configs)",
            url = "https://www.vpnbook.com/freevpn/wireguard-vpn",
            category = GuideCategory.FREE,
            descriptionRes = R.string.guide_desc_vpnbook,
            noteRes = R.string.guide_note_vpnbook
        ),

        // ------------------------------------------------------------------
        // PAID — providers with an official WireGuard config generator
        // ------------------------------------------------------------------
        GuideEntry(
            id = "mullvad",
            name = "Mullvad VPN",
            url = "https://mullvad.net/en/account/wireguard-config",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_mullvad
        ),
        GuideEntry(
            id = "windscribe",
            name = "Windscribe",
            url = "https://windscribe.com/getconfig/wireguard",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_windscribe,
            noteRes = R.string.guide_note_paid_only
        ),
        GuideEntry(
            id = "ivpn",
            name = "IVPN",
            url = "https://www.ivpn.net/clientarea/",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_ivpn
        ),
        GuideEntry(
            id = "azirevpn",
            name = "AzireVPN",
            url = "https://www.azirevpn.com",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_azirevpn
        ),
        GuideEntry(
            id = "ovpn",
            name = "OVPN",
            url = "https://www.ovpn.com",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_ovpn
        ),
        GuideEntry(
            id = "airvpn",
            name = "AirVPN",
            url = "https://airvpn.org",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_airvpn
        ),
        GuideEntry(
            id = "astrill",
            name = "Astrill",
            url = "https://www.astrill.com/member-zone/tools/wireguard-configuration",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_astrill
        ),
        GuideEntry(
            id = "hideme",
            name = "hide.me VPN",
            url = "https://hide.me",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_hideme
        ),
        GuideEntry(
            id = "ipvanish",
            name = "IPVanish",
            url = "https://www.ipvanish.com",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_ipvanish
        ),
        GuideEntry(
            id = "surfshark",
            name = "Surfshark",
            url = "https://surfshark.com",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_surfshark
        ),
        GuideEntry(
            id = "privatevpn",
            name = "PrivateVPN",
            url = "https://privatevpn.com",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_privatevpn
        ),
        GuideEntry(
            id = "privadovpn",
            name = "PrivadoVPN",
            url = "https://privadovpn.com",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_privadovpn
        ),
        GuideEntry(
            id = "strongvpn",
            name = "StrongVPN",
            url = "https://strongvpn.com",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_strongvpn
        ),
        GuideEntry(
            id = "torguard",
            name = "TorGuard",
            url = "https://torguard.net",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_torguard
        ),
        GuideEntry(
            id = "pia",
            name = "Private Internet Access (PIA)",
            url = "https://github.com/kylegrantlucas/pia-wg-config",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_pia,
            noteRes = R.string.guide_note_pia
        ),
        GuideEntry(
            id = "nordvpn",
            name = "NordVPN",
            url = "https://nordvpn.com",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_nordvpn,
            noteRes = R.string.guide_note_nordvpn
        ),
        GuideEntry(
            id = "purevpn",
            name = "PureVPN",
            url = "https://support.purevpn.com/en_US/account/how-to-add-wireguard-via-member-area",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_purevpn,
            noteRes = R.string.guide_note_purevpn
        ),
        GuideEntry(
            id = "12vpx",
            name = "12VPX",
            url = "https://12vpx.com",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_12vpx
        ),
        GuideEntry(
            id = "njalla",
            name = "Njalla",
            url = "https://njal.la/vpn",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_njalla,
            noteRes = R.string.guide_note_njalla
        ),
        GuideEntry(
            id = "cryptostorm",
            name = "Cryptostorm",
            url = "https://cryptostorm.is/wireguard",
            category = GuideCategory.PAID,
            descriptionRes = R.string.guide_desc_cryptostorm
        ),

        // ------------------------------------------------------------------
        // SELF-HOSTED — run your own WireGuard server (VPS / Raspberry Pi)
        // ------------------------------------------------------------------
        GuideEntry(
            id = "wg-easy",
            name = "wg-easy",
            url = "https://github.com/wg-easy/wg-easy",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_wg_easy
        ),
        GuideEntry(
            id = "pivpn",
            name = "PiVPN",
            url = "https://github.com/pivpn/pivpn",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_pivpn
        ),
        GuideEntry(
            id = "algo",
            name = "Algo VPN",
            url = "https://github.com/trailofbits/algo",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_algo
        ),
        GuideEntry(
            id = "headscale",
            name = "Headscale",
            url = "https://github.com/juanfont/headscale",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_headscale
        ),
        GuideEntry(
            id = "tailscale",
            name = "Tailscale (free personal plan)",
            url = "https://tailscale.com",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_tailscale
        ),
        GuideEntry(
            id = "netbird",
            name = "NetBird",
            url = "https://netbird.io",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_netbird
        ),
        GuideEntry(
            id = "netmaker",
            name = "Netmaker",
            url = "https://netmaker.io",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_netmaker
        ),
        GuideEntry(
            id = "firezone",
            name = "Firezone",
            url = "https://www.firezone.dev",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_firezone
        ),
        GuideEntry(
            id = "wireguard-install",
            name = "wireguard-install (one-command script)",
            url = "https://github.com/angristan/wireguard-install",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_angristan
        ),
        GuideEntry(
            id = "wg-portal",
            name = "WireGuard Portal",
            url = "https://github.com/h44z/wg-portal",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_wg_portal
        ),
        GuideEntry(
            id = "innernet",
            name = "innernet",
            url = "https://github.com/tonarino/innernet",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_innernet
        ),
        GuideEntry(
            id = "defguard",
            name = "defguard",
            url = "https://defguard.net",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_defguard
        ),
        GuideEntry(
            id = "amnezia",
            name = "Amnezia (self-hosted)",
            url = "https://amnezia.org/self-hosted",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_amnezia
        ),
        GuideEntry(
            id = "procustodibus",
            name = "Pro Custodibus",
            url = "https://www.procustodibus.com",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_procustodibus
        ),
        GuideEntry(
            id = "linuxserver-wireguard",
            name = "linuxserver/wireguard (Docker)",
            url = "https://docs.linuxserver.io/images/docker-wireguard/",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_lsio_wireguard
        ),
        GuideEntry(
            id = "wirehole",
            name = "WireHole (WireGuard + Pi-hole + Unbound)",
            url = "https://github.com/IAmStoxe/wirehole",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_wirehole
        ),
        GuideEntry(
            id = "pritunl",
            name = "Pritunl",
            url = "https://docs.pritunl.com/kb/vpn/getting-started/wireguard",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_pritunl,
            noteRes = R.string.guide_note_pritunl
        ),
        // v1.8.12 — self-hosted, free software
        GuideEntry(
            id = "wgdashboard",
            name = "WGDashboard",
            url = "https://github.com/donaldzou/WGDashboard",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_wgdashboard
        ),
        GuideEntry(
            id = "ansible-wireguard",
            name = "Ansible WireGuard role (githubixx)",
            url = "https://github.com/githubixx/ansible-role-wireguard",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_ansible_wg
        ),
        // v1.8.12 — self-hosted, the hosting bill: free tier vs paid VPS
        GuideEntry(
            id = "oracle-alwaysfree",
            name = "Oracle Cloud (Always Free VPS)",
            url = "https://www.oracle.com/cloud/free/",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_oracle_free,
            noteRes = R.string.guide_note_oracle_free
        ),
        GuideEntry(
            id = "hetzner-cloud",
            name = "Hetzner Cloud VPS",
            url = "https://www.hetzner.com/cloud",
            category = GuideCategory.SELF_HOSTED,
            descriptionRes = R.string.guide_desc_hetzner,
            noteRes = R.string.guide_note_hetzner
        ),

        // ------------------------------------------------------------------
        // ROUTERS — WireGuard built into the box that already runs your net
        // ------------------------------------------------------------------
        GuideEntry(
            id = "glinet",
            name = "GL.iNet routers",
            url = "https://www.gl-inet.com",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_glinet
        ),
        GuideEntry(
            id = "openwrt",
            name = "OpenWrt",
            url = "https://openwrt.org",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_openwrt
        ),
        GuideEntry(
            id = "opnsense",
            name = "pfSense & OPNsense",
            url = "https://opnsense.org",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_opnsense
        ),
        GuideEntry(
            id = "mikrotik",
            name = "MikroTik (RouterOS 7+)",
            url = "https://mikrotik.com",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_mikrotik
        ),
        GuideEntry(
            id = "asuswrt-merlin",
            name = "Asuswrt-Merlin",
            url = "https://www.asuswrt-merlin.net",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_asuswrt_merlin,
            noteRes = R.string.guide_note_asuswrt_merlin
        ),
        GuideEntry(
            id = "ddwrt",
            name = "DD-WRT",
            url = "https://dd-wrt.com",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_ddwrt
        ),
        GuideEntry(
            id = "vyos",
            name = "VyOS",
            url = "https://vyos.io",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_vyos
        ),
        GuideEntry(
            id = "unifi",
            name = "Ubiquiti UniFi gateways",
            url = "https://help.ui.com/hc/en-us/articles/115005445768-UniFi-Gateway-WireGuard-VPN-Server",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_unifi
        ),
        GuideEntry(
            id = "freshtomato",
            name = "FreshTomato",
            url = "https://wiki.freshtomato.org/doku.php/vpn-wireguard",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_freshtomato,
            noteRes = R.string.guide_note_freshtomato
        ),
        GuideEntry(
            id = "pfsense",
            name = "pfSense",
            url = "https://docs.netgate.com/pfsense/en/latest/vpn/wireguard/index.html",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_pfsense
        ),
        GuideEntry(
            id = "ipfire",
            name = "IPFire",
            url = "https://www.ipfire.org/docs/configuration/services/wireguard",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_ipfire
        ),
        GuideEntry(
            id = "asus-stock",
            name = "ASUS routers (stock firmware)",
            url = "https://www.asus.com/support/faq/1048280",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_asus_stock,
            noteRes = R.string.guide_note_asus_stock
        ),
        // v1.8.12 — free firewall OS
        GuideEntry(
            id = "openbsd-wg",
            name = "OpenBSD (wg(4) firewall)",
            url = "https://man.openbsd.org/wg",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_openbsd
        ),
        // v1.8.12 — paid firewall appliances & routers
        GuideEntry(
            id = "firewalla",
            name = "Firewalla (Gold / Purple)",
            url = "https://firewalla.com",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_firewalla,
            noteRes = R.string.guide_note_firewalla
        ),
        GuideEntry(
            id = "netgate-appliance",
            name = "Netgate pfSense+ appliances",
            url = "https://www.netgate.com/appliances",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_netgate_appliance,
            noteRes = R.string.guide_note_netgate
        ),
        GuideEntry(
            id = "keenetic",
            name = "Keenetic routers",
            url = "https://keenetic.com",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_keenetic,
            noteRes = R.string.guide_note_keenetic
        ),
        GuideEntry(
            id = "turris-omnia",
            name = "Turris Omnia",
            url = "https://omnia.turris.cz/en/",
            category = GuideCategory.ROUTERS,
            descriptionRes = R.string.guide_desc_turris
        ),

        // ------------------------------------------------------------------
        // APPS & TOOLS — official clients that also read the same .conf
        // ------------------------------------------------------------------
        GuideEntry(
            id = "wg-android",
            name = "WireGuard for Android (Google Play)",
            url = "https://play.google.com/store/apps/details?id=com.wireguard.android",
            category = GuideCategory.APPS,
            descriptionRes = R.string.guide_desc_wg_android
        ),
        GuideEntry(
            id = "wg-fdroid",
            name = "WireGuard for Android (F-Droid)",
            url = "https://f-droid.org/packages/com.wireguard.android/",
            category = GuideCategory.APPS,
            descriptionRes = R.string.guide_desc_wg_fdroid
        ),
        GuideEntry(
            id = "wg-desktop",
            name = "WireGuard for desktop (official)",
            url = "https://www.wireguard.com/install/",
            category = GuideCategory.APPS,
            descriptionRes = R.string.guide_desc_wg_desktop
        ),
        GuideEntry(
            id = "wg-ios",
            name = "WireGuard for iOS (App Store)",
            url = "https://apps.apple.com/app/wireguard/id1441195209",
            category = GuideCategory.APPS,
            descriptionRes = R.string.guide_desc_wg_ios
        ),
        GuideEntry(
            id = "wg-macos",
            name = "WireGuard for macOS (App Store)",
            url = "https://apps.apple.com/app/wireguard/id1451685025",
            category = GuideCategory.APPS,
            descriptionRes = R.string.guide_desc_wg_macos
        ),
        GuideEntry(
            id = "wiresock",
            name = "WireSock Secure Connect (Windows)",
            url = "https://wiresock.net",
            category = GuideCategory.APPS,
            descriptionRes = R.string.guide_desc_wiresock,
            noteRes = R.string.guide_note_wiresock
        ),
        GuideEntry(
            id = "networkmanager",
            name = "NetworkManager (Linux built-in)",
            url = "https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/9/html/configuring_and_managing_networking/assembly_setting-up-a-wireguard-vpn_configuring-and-managing-networking",
            category = GuideCategory.APPS,
            descriptionRes = R.string.guide_desc_networkmanager
        )
    )

    /**
     * v1.8 — search keywords per entry id (EN brand aliases + Arabic
     * transliterations), consumed by [WireGuideFilter]. Kept here so the
     * search vocabulary lives next to the catalog data it describes.
     */
    private val keywordsById: Map<String, List<String>> = mapOf(
        "cloudflare-warp-wgcf" to listOf("cloudflare", "warp", "wgcf", "1.1.1.1",
            "كلاودفلير", "وارب", "ورب"),
        "proton-free" to listOf("proton", "switzerland", "بروتون"),
        "mullvad" to listOf("mullvad", "sweden", "مولفاد", "السويد"),
        "windscribe" to listOf("windscribe", "ويندسكرايب"),
        "ivpn" to listOf("ivpn", "آيفن"),
        "azirevpn" to listOf("azire", "sweden", "أزير"),
        "ovpn" to listOf("ovpn", "sweden", "أوفبن"),
        "airvpn" to listOf("air", "italy", "اير", "إيطاليا"),
        "astrill" to listOf("astrill", "استريل"),
        "hideme" to listOf("hideme", "hide.me", "هايدمي"),
        "ipvanish" to listOf("ipvanish", "ايفانيش"),
        "surfshark" to listOf("surfshark", "سيرفشارك"),
        "privatevpn" to listOf("privatevpn", "private", "برايفت"),
        "privadovpn" to listOf("privado", "بريفادو"),
        "strongvpn" to listOf("strong", "سترونج"),
        "torguard" to listOf("torguard", "تورجارد"),
        "pia" to listOf("pia", "private internet access", "برايفات انترنيت"),
        "nordvpn" to listOf("nord", "norway", "نورد"),
        "purevpn" to listOf("purevpn", "pure", "بيور", "بور في بي ان"),
        "12vpx" to listOf("12vpx", "dutch", "12 في بي اكس", "هولندي"),
        "njalla" to listOf("njalla", "njal", "crypto", "نيالا", "نچالا"),
        "cryptostorm" to listOf("cryptostorm", "cstorm", "token", "كريبتو ستورم", "رمز"),
        "vpnbook" to listOf("vpnbook", "free", "no signup", "في بي بوك", "مجاني", "بدون تسجيل"),
        "wg-easy" to listOf("docker", "web ui", "دوكر", "ويب"),
        "pivpn" to listOf("raspberry", "pi", "debian", "رازبيري", "راسبيري"),
        "algo" to listOf("ansible", "trail of bits", "أنسيبل"),
        "headscale" to listOf("tailscale", "self", "هيدسكيل"),
        "riseup" to listOf("riseup", "bitmask", "donation", "non-profit", "رايزب", "ريز اب", "مجاني"),
        "tailscale" to listOf("mesh", "personal", "تيلسكيل", "ميش"),
        "netbird" to listOf("netbird", "mesh", "نت بيرد"),
        "netmaker" to listOf("netmaker", "mesh", "نت ميكر"),
        "firezone" to listOf("firezone", "zero trust", "فايرزون"),
        "wireguard-install" to listOf("script", "bash", "ubuntu", "سكربت", "سكريبت"),
        "wg-portal" to listOf("portal", "ldap", "oauth", "web ui", "بوابة", "ويب"),
        "innernet" to listOf("overlay", "cidr", "rust", "اينرنت"),
        "defguard" to listOf("mfa", "2fa", "zero trust", "rust", "دى فجارد", "دفقارد"),
        "amnezia" to listOf("amnezia", "amneziawg", "obfuscation", "censorship", "امنزيا", "حجب"),
        "procustodibus" to listOf("custodibus", "monitoring", "agent", "برو كوستوديبوس", "مراقبة"),
        "linuxserver-wireguard" to listOf("linuxserver", "lsio", "docker", "لينكس سيرفر", "دوكر"),
        "wirehole" to listOf("wirehole", "pi-hole", "unbound", "وايرهول", "باي هول", "أونباوند"),
        "pritunl" to listOf("pritunl", "enterprise", "openvpn", "بريتونل", "مؤسسي"),
        "wgdashboard" to listOf("wgdashboard", "dashboard", "web ui", "peers", "free",
            "لوحة تحكم", "داشبورد", "مجاني"),
        "ansible-wireguard" to listOf("ansible", "automation", "iac", "free",
            "أنسيبل", "أتمتة", "مجاني"),
        "oracle-alwaysfree" to listOf("oracle", "always free", "vps", "arm", "free",
            "أوراكل", "سيرفر مجاني", "مجاني"),
        "hetzner-cloud" to listOf("hetzner", "vps", "cloud", "paid",
            "هتزنر", "سيرفر", "مدفوع"),
        "openbsd-wg" to listOf("openbsd", "unix", "firewall", "free",
            "أوبن بي إس دي", "جدار ناري", "مجاني"),
        "firewalla" to listOf("firewalla", "gold", "purple", "appliance", "firewall", "paid",
            "فايرولا", "جدار ناري", "مدفوع"),
        "netgate-appliance" to listOf("netgate", "pfsense plus", "appliance", "hardware", "firewall", "paid",
            "نتغيت", "جدار ناري", "مدفوع"),
        "keenetic" to listOf("keenetic", "keeneticos", "router", "paid",
            "كينيتك", "مدفوع"),
        "turris-omnia" to listOf("turris", "omnia", "open source", "router", "paid",
            "توريس", "أومنيا", "مدفوع"),
        "glinet" to listOf("gl-inet", "travel", "سفر", "راوتر سفر"),
        "openwrt" to listOf("openwrt", "luci", "اوبنوورت"),
        "opnsense" to listOf("pfsense", "opnsense", "firewall", "جدار ناري"),
        "mikrotik" to listOf("routeros", "ميكروتك"),
        "asuswrt-merlin" to listOf("asus", "merlin", "firmware", "ايسوس", "مرلين"),
        "ddwrt" to listOf("dd-wrt", "broadcom", "firmware", "دي دي"),
        "vyos" to listOf("vyatta", "linux router", "فايوس", "في أو إس"),
        "unifi" to listOf("ubiquiti", "udm", "dream machine", "unifi", "يونيفي", "يويبيكوتي"),
        "freshtomato" to listOf("tomato", "broadcom", "freshtomato", "فريش طوماتو"),
        "pfsense" to listOf("pfsense", "netgate", "firewall", "بي ف سينس", "جدار ناري"),
        "ipfire" to listOf("ipfire", "firewall", "آي فاير", "جدار ناري"),
        "asus-stock" to listOf("asus", "stock", "vpn fusion", "ايسوس", "أصلي"),
        "wg-android" to listOf("android", "official", "اندرويد"),
        "wg-fdroid" to listOf("fdroid", "open source", "فدرويد"),
        "wg-desktop" to listOf("windows", "linux", "mac", "ويندوز", "لينكس"),
        "wg-ios" to listOf("ios", "iphone", "ipad", "ايفون", "آيباد", "ابل"),
        "wg-macos" to listOf("macos", "mac", "ماك", "ماك بوك", "ابل"),
        "wiresock" to listOf("wiresock", "windows", "third-party", "وايرسوك"),
        "networkmanager" to listOf("networkmanager", "linux", "nmcli", "لينكس", "مدير الشبكة")
    )

    /** Curated search keywords for one entry id (never empty for real ids). */
    fun keywordsOf(id: String): List<String> = keywordsById[id] ?: emptyList()

    /**
     * v1.8.2 — "Editor's choice": the curated shortlist of the most reliable
     * sources in the catalog, badged on every card and used as a sort
     * tie-break. Criteria — official WireGuard project artifacts, open-source
     * and independently audited providers, or tools with a long track record
     * and a large maintained community:
     *  - mullvad / ivpn / proton-free: open-source clients, published no-logs
     *    audits, direct .conf generation;
     *  - wg-easy / pivpn / tailscale: the most widely maintained self-host
     *    paths (top-starred GitHub projects and mainstream adoption);
     *  - openwrt: the reference open-source router platform;
     *  - wg-android / wg-fdroid / wg-desktop: the official WireGuard clients.
     */
    val editorsChoice: Set<String> = setOf(
        "mullvad", "ivpn", "proton-free",
        "wg-easy", "pivpn", "tailscale",
        "openwrt",
        "wg-android", "wg-fdroid", "wg-desktop"
    )

    /** True when [id] carries the "Editor's choice" reliability badge. */
    fun isEditorsChoice(id: String): Boolean = id in editorsChoice

    /** Entries of one category, in catalog order. */
    fun byCategory(category: GuideCategory): List<GuideEntry> =
        entries.filter { it.category == category }

    /**
     * v1.8.3 — entry lookup by id; null for unknown ids (e.g. a signals
     * snapshot restored from a backup of a newer catalog version), so
     * statistics and UI rows can degrade gracefully.
     */
    fun byId(id: String): GuideEntry? = entriesById[id]

    private val entriesById: Map<String, GuideEntry> by lazy {
        entries.associateBy { it.id }
    }

    /** Ordered section list for the guide screen. */
    fun sections(): List<Pair<GuideCategory, List<GuideEntry>>> =
        GuideCategory.entries.map { it to byCategory(it) }.filter { it.second.isNotEmpty() }

    /**
     * v1.7.1 — per-category step-by-step tutorial shown as a mini card under
     * each section header (a string-array per category, EN + AR).
     */
    @StringRes
    fun stepsRes(category: GuideCategory): Int = when (category) {
        GuideCategory.FREE -> R.array.guide_steps_free
        GuideCategory.PAID -> R.array.guide_steps_paid
        GuideCategory.SELF_HOSTED -> R.array.guide_steps_selfhosted
        GuideCategory.ROUTERS -> R.array.guide_steps_routers
        GuideCategory.APPS -> R.array.guide_steps_apps
    }
}
