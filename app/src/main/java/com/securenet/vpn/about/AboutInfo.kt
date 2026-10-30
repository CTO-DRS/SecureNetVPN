package com.securenet.vpn.about

/**
 * v1.8.18 — 📇 developer contact directory (معلومات التواصل بالمطوّر), sourced
 * verbatim from the developer's shared contact card PDF (CTO.DRS): every
 * channel the developer actually publishes, with its canonical handle and
 * official URL — nothing invented, nothing inferred.
 *
 * Shown in Settings → "Support & developer" (الدعم والتواصل) via a bottom
 * sheet where each row deep-links into the matching app or browser. The
 * directory is pure data (plain strings, zero Android imports) so the whole
 * shape is pinned by JVM tests ([AboutInfoTest] asserts the channel count,
 * the https-only rule, per-channel literal URL anchors and uniqueness).
 */
object AboutInfo {

    /** Public developer persona, exactly as printed on the contact card. */
    const val DEVELOPER_NAME = "CTO.DRS"

    /** One contactable channel: display name, public handle and official URL. */
    data class Contact(
        val name: String,
        val handle: String,
        val url: String
    )

    /** Every published channel, in the card's own presentation order. */
    val contacts: List<Contact> = listOf(
        Contact(
            "WhatsApp",
            "wa.me/message/MVTVWPOFOVFF1",
            "https://wa.me/message/MVTVWPOFOVFF1"
        ),
        Contact(
            "Telegram",
            "t.me/CTO_DRS",
            "https://t.me/CTO_DRS"
        ),
        Contact(
            "Instagram",
            "@cto.drs",
            "https://www.instagram.com/cto.drs"
        ),
        Contact(
            "TikTok",
            "@cto.drs",
            "https://www.tiktok.com/@cto.drs"
        ),
        Contact(
            "X",
            "@cto_drs",
            "https://x.com/cto_drs"
        ),
        Contact(
            "YouTube",
            "@cto-drs",
            "https://youtube.com/@cto-drs"
        ),
        Contact(
            "GitHub",
            "CTO-DRS",
            "https://github.com/CTO-DRS"
        ),
        Contact(
            "Website",
            "cto-drs.xo.je",
            "https://cto-drs.xo.je"
        )
    )
}
