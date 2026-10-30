package com.securenet.vpn.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.18 — pins the developer contact directory to the values on the
 * shared contact card PDF, channel by channel: the directory is what the
 * Settings → Support sheet renders and what tapping a row opens, so every
 * URL is anchored literally — a typo here would silently send users to the
 * wrong account.
 */
class AboutInfoTest {

    @Test
    fun `directory exposes exactly the eight published channels`() {
        assertEquals(
            listOf(
                "WhatsApp", "Telegram", "Instagram", "TikTok",
                "X", "YouTube", "GitHub", "Website"
            ),
            AboutInfo.contacts.map { it.name }
        )
    }

    @Test
    fun `every channel is https and carries a visible handle`() {
        AboutInfo.contacts.forEach { contact ->
            assertTrue("${contact.name} must start with https", contact.url.startsWith("https://"))
            assertTrue("${contact.name} must print a handle", contact.handle.isNotBlank())
            assertTrue("${contact.name} handle must appear in its URL", contact.url.contains(contact.handle.removePrefix("@")))
        }
    }

    @Test
    fun `whatsapp channel anchors to the business-chat link from the card`() {
        val whatsapp = AboutInfo.contacts.first { it.name == "WhatsApp" }
        assertEquals("https://wa.me/message/MVTVWPOFOVFF1", whatsapp.url)
    }

    @Test
    fun `social channels anchor to their exact official profiles`() {
        assertEquals(
            "https://t.me/CTO_DRS",
            AboutInfo.contacts.first { it.name == "Telegram" }.url
        )
        assertEquals(
            "https://www.instagram.com/cto.drs",
            AboutInfo.contacts.first { it.name == "Instagram" }.url
        )
        assertEquals(
            "https://www.tiktok.com/@cto.drs",
            AboutInfo.contacts.first { it.name == "TikTok" }.url
        )
        assertEquals(
            "https://x.com/cto_drs",
            AboutInfo.contacts.first { it.name == "X" }.url
        )
    }

    @Test
    fun `youtube github and website anchor to the developer identity`() {
        assertEquals(
            "https://youtube.com/@cto-drs",
            AboutInfo.contacts.first { it.name == "YouTube" }.url
        )
        assertEquals(
            "https://github.com/CTO-DRS",
            AboutInfo.contacts.first { it.name == "GitHub" }.url
        )
        assertEquals(
            "https://cto-drs.xo.je",
            AboutInfo.contacts.first { it.name == "Website" }.url
        )
    }

    @Test
    fun `urls are unique so no channel shadows another`() {
        assertEquals(
            AboutInfo.contacts.size,
            AboutInfo.contacts.map { it.url }.toSet().size
        )
    }
}
