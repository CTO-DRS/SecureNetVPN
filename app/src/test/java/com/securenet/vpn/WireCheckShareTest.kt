package com.securenet.vpn

import com.securenet.vpn.util.WireCheckShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.19 — pins the checker report's shareable text: header with the app
 * version, verdict, parsed shape, one bullet per issue, and the honesty
 * footer — and that a clean check (no issues) simply omits the bullet
 * section instead of printing an empty list.
 */
class WireCheckShareTest {

    @Test
    fun `report lists header, verdict, shape and every issue bullet`() {
        val text = WireCheckShare.shareText(
            appVersion = "1.8.19",
            resultLine = "3 errors, 2 warnings",
            shapeLine = "1 × [Interface] · 2 × [Peer]",
            issues = listOf("❌ line 3 — missing PrivateKey", "⚠️ line 9 — MTU below 1280")
        )
        assertTrue(text.startsWith("SecureNet VPN v1.8.19 — WireGuard config check\n"))
        assertTrue(text.contains("3 errors, 2 warnings\n"))
        assertTrue(text.contains("1 × [Interface] · 2 × [Peer]\n"))
        assertTrue(text.contains("• ❌ line 3 — missing PrivateKey\n"))
        assertTrue(text.contains("• ⚠️ line 9 — MTU below 1280\n"))
        assertTrue(text.endsWith("Checked locally on the device — nothing was uploaded."))
    }

    @Test
    fun `clean check omits the bullet section entirely`() {
        val text = WireCheckShare.shareText(
            appVersion = "1.8.19",
            resultLine = "Looks good — no problems found",
            shapeLine = "1 × [Interface] · 1 × [Peer]",
            issues = emptyList()
        )
        assertTrue(!text.contains("•"))
        assertEquals(
            "SecureNet VPN v1.8.19 — WireGuard config check\n" +
                "Looks good — no problems found\n" +
                "1 × [Interface] · 1 × [Peer]\n" +
                "\nChecked locally on the device — nothing was uploaded.",
            text
        )
    }
}
