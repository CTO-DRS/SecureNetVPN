package com.securenet.vpn.guide

import androidx.annotation.StringRes
import com.securenet.vpn.R

/**
 * v1.7 — Catalog of sources for obtaining WireGuard profiles that import
 * straight into SecureNet (Servers tab → Import). Four categories cover the
 * full spectrum the user asked for: free services, paid providers with a
 * config generator, self-hosted server software, and official clients/tools.
 *
 * The catalog is pure data so it can be unit-tested (URL shape, uniqueness,
 * category completeness) without any Android dependency beyond @StringRes.
 * Descriptions live in strings.xml (EN) and values-ar/strings.xml (AR).
 *
 * URLs were verified against the providers' own documentation (dashboards,
 * knowledge bases and the GL.iNet/TP-Link provider guides) at release time.
 */
enum class GuideCategory {
    FREE, PAID, SELF_HOSTED, APPS
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
        )
    )

    /** Entries of one category, in catalog order. */
    fun byCategory(category: GuideCategory): List<GuideEntry> =
        entries.filter { it.category == category }

    /** Ordered section list for the guide screen. */
    fun sections(): List<Pair<GuideCategory, List<GuideEntry>>> =
        GuideCategory.entries.map { it to byCategory(it) }.filter { it.second.isNotEmpty() }
}
