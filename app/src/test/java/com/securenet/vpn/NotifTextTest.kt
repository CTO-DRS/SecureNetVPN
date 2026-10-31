package com.securenet.vpn

import com.securenet.vpn.service.NotifText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.21 — the pure text engine behind the richer persistent
 * notification. Every string the notification shade renders must be
 * deterministic: byte/speed ladders with trimmed decimals, the compact
 * session line, the expanded three-line body and a timezone-safe start
 * clock derived from a caller-supplied UTC offset.
 */
class NotifTextTest {

    @Test
    fun `bytes ladder covers B, KB, MB, GB with trimmed decimals`() {
        assertEquals("0 B", NotifText.formatBytes(0L))
        assertEquals("512 B", NotifText.formatBytes(512L))
        assertEquals("1 KB", NotifText.formatBytes(1024L))
        assertEquals("1.5 KB", NotifText.formatBytes(1536L))
        assertEquals("1 MB", NotifText.formatBytes(1024L * 1024L))
        assertEquals("1.5 MB", NotifText.formatBytes(1536L * 1024L))
        assertEquals("1 GB", NotifText.formatBytes(1024L * 1024L * 1024L))
    }

    @Test
    fun `negative bytes clamp to zero`() {
        assertEquals("0 B", NotifText.formatBytes(-5L))
        assertEquals("0 B/s", NotifText.formatSpeed(-5L))
    }

    @Test
    fun `speed appends per-second suffix`() {
        assertEquals("340 B/s", NotifText.formatSpeed(340L))
        assertEquals("1.2 MB/s", NotifText.formatSpeed(1_258_291L))
    }

    @Test
    fun `session line carries both directional totals`() {
        val line = NotifText.sessionLine(1_258_291L, 348_160L)
        assertEquals("↓ 1.2 MB · ↑ 340 KB", line)
    }

    @Test
    fun `expanded body renders three lines when the session anchor exists`() {
        val body = NotifText.expandedBody(
            rxBytes = 1_258_291L, txBytes = 348_160L,
            rxSpeed = 3_571_712L, txSpeed = 122_880L,
            startedAtMillis = 9_000_000L, utcOffsetMillis = 0L,
            downLabel = "Download", upLabel = "Upload", sinceLabel = "Since"
        )
        assertEquals(
            "Download 1.2 MB (3.4 MB/s)\n" +
                "Upload 340 KB (120 KB/s)\n" +
                "Since 02:30",
            body
        )
    }

    @Test
    fun `expanded body drops the clock line without a session anchor`() {
        val body = NotifText.expandedBody(
            rxBytes = 1024L, txBytes = 2048L,
            rxSpeed = 0L, txSpeed = 0L,
            startedAtMillis = 0L, utcOffsetMillis = 0L,
            downLabel = "Download", upLabel = "Upload", sinceLabel = "Since"
        )
        assertEquals("Download 1 KB (0 B/s)\nUpload 2 KB (0 B/s)", body)
    }

    @Test
    fun `clock pads single digits and rolls over the day`() {
        // 00:07 local — padding keeps the leading zero.
        assertEquals("00:07", NotifText.clock(7L * 60_000L, 0L))
        // 23:59 local.
        assertEquals("23:59", NotifText.clock((23L * 60 + 59) * 60_000L, 0L))
        // Midnight rollover into the next day renders 00:00, not 24:00.
        assertEquals(
            "00:00",
            NotifText.clock(24L * 60L * 60_000L - 1L + 1_000L, 0L)
        )
    }

    @Test
    fun `clock honours a non-zero utc offset`() {
        // 10:00 UTC + 3h offset = 13:00 local.
        assertEquals(
            "13:00",
            NotifText.clock(10L * 60L * 60_000L, 3L * 60L * 60_000L)
        )
        // Negative offset: 10:00 UTC − 5h = 05:00 local.
        assertEquals(
            "05:00",
            NotifText.clock(10L * 60L * 60_000L, -5L * 60L * 60_000L)
        )
    }

    @Test
    fun `arabic labels flow through the expanded body unchanged`() {
        val body = NotifText.expandedBody(
            rxBytes = 1024L, txBytes = 512L,
            rxSpeed = 1024L, txSpeed = 0L,
            startedAtMillis = 3_600_000L, utcOffsetMillis = 0L,
            downLabel = "التنزيل", upLabel = "الرفع", sinceLabel = "بدأت الجلسة"
        )
        assertTrue(body.contains("التنزيل 1 KB (1 KB/s)"))
        assertTrue(body.contains("الرفع 512 B (0 B/s)"))
        assertTrue(body.contains("بدأت الجلسة 01:00"))
    }

    // ------------------------------------------------------------------
    // v1.8.23 — duration ladder, session average, last-session summary
    // ------------------------------------------------------------------

    @Test
    fun `duration ladder covers seconds, minutes and hours`() {
        assertEquals("45 s", NotifText.formatDuration(45_000L))
        assertEquals("42 min", NotifText.formatDuration(42L * 60_000L))
        assertEquals("1 h 5 min", NotifText.formatDuration(3_900_000L))
        assertEquals("2 h 0 min", NotifText.formatDuration(7_200_000L))
        assertEquals("0 s", NotifText.formatDuration(-1_000L))
    }

    @Test
    fun `average line requires a session anchor and a positive elapsed second`() {
        assertNull(
            NotifText.averageLine(1000L, 1000L, startedAtMillis = 0L, nowMillis = 10_000L, avgLabel = "Average")
        )
        // Negative started-at is as good as no anchor.
        assertNull(
            NotifText.averageLine(1000L, 1000L, startedAtMillis = -5L, nowMillis = 10_000L, avgLabel = "Average")
        )
        // Less than one elapsed second — the live rates already cover it.
        assertNull(
            NotifText.averageLine(1000L, 1000L, startedAtMillis = 1_000L, nowMillis = 1_500L, avgLabel = "Average")
        )
    }

    @Test
    fun `average line divides totals by elapsed seconds`() {
        val line = NotifText.averageLine(
            rxBytes = 1_258_291L, txBytes = 348_160L,
            startedAtMillis = 1_000L, nowMillis = 101_000L,
            avgLabel = "Average"
        )
        assertEquals("Average ↓ 12.3 KB/s · ↑ 3.4 KB/s", line)
    }

    @Test
    fun `last session line carries duration and both totals`() {
        assertEquals(
            "Last session 42 min · ↓ 156 MB · ↑ 12 MB",
            NotifText.lastSessionLine(
                durationMillis = 42L * 60_000L,
                rxBytes = 163_577_856L,
                txBytes = 12_582_912L,
                label = "Last session"
            )
        )
        // A very short failed session still reads honestly.
        assertEquals(
            "Last session 12 s · ↓ 0 B · ↑ 0 B",
            NotifText.lastSessionLine(12_000L, 0L, 0L, "Last session")
        )
    }

    @Test
    fun `detailed body appends the average line when a session anchor exists`() {
        val body = NotifText.detailedBody(
            rxBytes = 1_258_291L, txBytes = 348_160L,
            rxSpeed = 3_571_712L, txSpeed = 122_880L,
            startedAtMillis = 9_000_000L,
            nowMillis = 9_100_000L,
            utcOffsetMillis = 0L,
            downLabel = "Download", upLabel = "Upload",
            sinceLabel = "Since", avgLabel = "Average"
        )
        assertEquals(
            "Download 1.2 MB (3.4 MB/s)\n" +
                "Upload 340 KB (120 KB/s)\n" +
                "Since 02:30\n" +
                "Average ↓ 12.3 KB/s · ↑ 3.4 KB/s",
            body
        )
    }

    @Test
    fun `detailed body without an anchor keeps the two-line shape`() {
        val body = NotifText.detailedBody(
            rxBytes = 1024L, txBytes = 2048L,
            rxSpeed = 0L, txSpeed = 0L,
            startedAtMillis = 0L, nowMillis = 5_000L,
            utcOffsetMillis = 0L,
            downLabel = "Download", upLabel = "Upload",
            sinceLabel = "Since", avgLabel = "Average"
        )
        assertEquals("Download 1 KB (0 B/s)\nUpload 2 KB (0 B/s)", body)
    }

    @Test
    fun `arabic labels flow through the v1_8_23 lines unchanged`() {
        assertTrue(
            NotifText.lastSessionLine(
                42L * 60_000L, 163_577_856L, 12_582_912L, "الجلسة الأخيرة"
            ).contains("الجلسة الأخيرة 42 min · ↓ 156 MB · ↑ 12 MB")
        )
        assertEquals(
            "المتوسط ↓ 12.3 KB/s · ↑ 3.4 KB/s",
            NotifText.averageLine(
                1_258_291L, 348_160L, 1_000L, 101_000L, "المتوسط"
            )
        )
        assertTrue(
            NotifText.detailedBody(
                1_258_291L, 348_160L, 3_571_712L, 122_880L,
                9_000_000L, 9_100_000L, 0L,
                "التنزيل", "الرفع", "بدأت الجلسة", "المتوسط"
            ).endsWith("المتوسط ↓ 12.3 KB/s · ↑ 3.4 KB/s")
        )
    }
}
