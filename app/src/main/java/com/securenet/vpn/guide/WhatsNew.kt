package com.securenet.vpn.guide

import androidx.annotation.StringRes
import com.securenet.vpn.R

/**
 * v1.8.17 — 📊 "What's new" in-app release feed (صفحة «ما الجديد»).
 * The changelog lives inside the app, newest first: the guide screen
 * auto-opens this sheet once per version (a stored marker remembers the
 * last acknowledged version) and a tools-row button reopens it any time,
 * so updates introduce themselves instead of arriving silently.
 *
 * The data is deliberately a static snapshot of SHIPPED releases — one
 * bullet per headline feature, in plain user-facing language, EN + AR.
 * [CURRENT_VERSION] is the version this build presents; the guide screen
 * stores it after showing the sheet, so the auto-open fires exactly once
 * per release. Both constants live in the same file that gains a new
 * [Release] row every version, so they can never drift apart.
 *
 * Pure data (ids + strings only) — the whole shape is unit-testable on
 * the JVM ([WhatsNewTest] pins the ordering, the current-version anchor
 * and the per-release content).
 */
object WhatsNew {

    /** The version this build ships; differs from the stored marker ⇒ auto-open once. */
    const val CURRENT_VERSION = "1.8.22"

    /** Where every shipped release lives (GitHub Releases), used by the share button. */
    const val RELEASES_URL = "https://github.com/CTO-DRS/SecureNetVPN/releases"

    /** One shipped release: its public version name and headline bullets. */
    data class Release(
        val version: String,
        @StringRes val items: List<Int>
    )

    /**
     * v1.8.18 — capability badges rendered under the current release in the
     * what's-new sheet: what the difficulty filter covers and which languages
     * the checker UI now speaks (8 total: the built-in AR/EN plus the six
     * v1.8.17 translations). Pure string ids, JVM-pinned via [WhatsNewTest].
     */
    @StringRes
    val currentBadges: List<Int> = listOf(
        R.string.badge_difficulty,
        R.string.badge_checker_langs
    )

    /**
     * v1.8.18 — the text the "share this release" button sends: a neutral
     * EN header (shares go anywhere), the version's own bullets and the
     * releases link. Pure so [WhatsNewTest] pins the exact shape.
     */
    fun shareText(version: String, bullets: List<String>): String = buildString {
        append("SecureNet VPN v").append(version).append(" — What's new:\n")
        bullets.forEach { append("• ").append(it).append('\n') }
        append("\nDownload & source: ").append(RELEASES_URL)
    }

    /** Shipped releases, newest first (only versions that added user-facing features). */
    val releases: List<Release> = listOf(
        Release(
            "1.8.22",
            listOf(
                R.string.whatsnew_1822_1,
                R.string.whatsnew_1822_2
            )
        ),
        Release(
            "1.8.21",
            listOf(
                R.string.whatsnew_1821_1
            )
        ),
        Release(
            "1.8.20",
            listOf(
                R.string.whatsnew_1820_1,
                R.string.whatsnew_1820_2
            )
        ),
        Release(
            "1.8.19",
            listOf(
                R.string.whatsnew_1819_1,
                R.string.whatsnew_1819_2,
                R.string.whatsnew_1819_3
            )
        ),
        Release(
            "1.8.18",
            listOf(
                R.string.whatsnew_1818_1,
                R.string.whatsnew_1818_2,
                R.string.whatsnew_1818_3
            )
        ),
        Release(
            "1.8.17",
            listOf(
                R.string.whatsnew_1817_1,
                R.string.whatsnew_1817_2,
                R.string.whatsnew_1817_3
            )
        ),
        Release(
            "1.8.16",
            listOf(
                R.string.whatsnew_1816_1,
                R.string.whatsnew_1816_2
            )
        ),
        Release(
            "1.8.15",
            listOf(
                R.string.whatsnew_1815_1,
                R.string.whatsnew_1815_2
            )
        ),
        Release(
            "1.8.14",
            listOf(
                R.string.whatsnew_1814_1,
                R.string.whatsnew_1814_2
            )
        ),
        Release(
            "1.8.13",
            listOf(
                R.string.whatsnew_1813_1,
                R.string.whatsnew_1813_2,
                R.string.whatsnew_1813_3
            )
        ),
        Release(
            "1.8.12",
            listOf(
                R.string.whatsnew_1812_1
            )
        )
    )
}
