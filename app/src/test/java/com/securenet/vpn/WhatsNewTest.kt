package com.securenet.vpn

import com.securenet.vpn.guide.WhatsNew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.17/18 — the 📊 in-app "What's new" feed. The sheet is only as honest
 * as this data: versions must read newest-first, [WhatsNew.CURRENT_VERSION]
 * must sit at the top (the auto-open fires when the stored marker differs
 * from it), and every release must actually carry bullets — an empty feed
 * or a mid-list current version would make updates announce themselves
 * wrongly. v1.8.18 adds the capability badges (difficulty filter + checker
 * languages) and the share-text builder rendered by the sheet.
 */
class WhatsNewTest {

    private fun key(version: String): Int {
        val parts = version.removePrefix("v").split(".")
        return parts[0].toInt() * 10_000 + parts[1].toInt() * 100 + parts[2].toInt()
    }

    @Test
    fun `feed is not empty and starts at the current version`() {
        assertTrue(WhatsNew.releases.isNotEmpty())
        assertEquals("1.8.19", WhatsNew.CURRENT_VERSION)
        assertEquals(WhatsNew.CURRENT_VERSION, WhatsNew.releases.first().version)
    }

    @Test
    fun `versions are strictly descending and unique`() {
        val keys = WhatsNew.releases.map { key(it.version) }
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(
            keys.zipWithNext().all { (a, b) -> a > b }
        )
    }

    @Test
    fun `every release carries at least one bullet`() {
        assertTrue(WhatsNew.releases.all { it.items.isNotEmpty() })
    }

    @Test
    fun `shipped releases are anchored by literal version`() {
        // v1.8.12 … v1.8.19 — the releases with user-facing changelog lines.
        assertEquals(
            listOf(
                "1.8.19", "1.8.18", "1.8.17", "1.8.16",
                "1.8.15", "1.8.14", "1.8.13", "1.8.12"
            ),
            WhatsNew.releases.map { it.version }
        )
    }

    @Test
    fun `current release carries the capability badges`() {
        // v1.8.18 — the difficulty-coverage chip and the checker-languages chip.
        assertEquals(2, WhatsNew.currentBadges.size)
        assertTrue(WhatsNew.currentBadges.distinct().size == WhatsNew.currentBadges.size)
    }

    @Test
    fun `share text names the version, every bullet and the releases link`() {
        val text = WhatsNew.shareText(
            WhatsNew.CURRENT_VERSION,
            listOf("first headline", "second headline")
        )
        assertTrue(text.startsWith("SecureNet VPN v1.8.19 — What's new:"))
        assertTrue(text.contains("• first headline"))
        assertTrue(text.contains("• second headline"))
        assertTrue(text.contains(WhatsNew.RELEASES_URL))
        assertEquals(
            "https://github.com/CTO-DRS/SecureNetVPN/releases",
            WhatsNew.RELEASES_URL
        )
    }
}
