package com.securenet.vpn

import com.securenet.vpn.guide.GuideProgress
import com.securenet.vpn.guide.InMemoryGuideProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.15 — the pure setup-wizard progress store behind the guide's
 * interactive checklist (معالج الإعداد التفاعلي). The detail page paints
 * whatever this returns, so the bounds rules matter: a stale or negative
 * step index must never surface as a checked step, unchecking the last
 * step must fully forget the source, and one source's progress must never
 * leak into another's.
 */
class GuideProgressTest {

    private val progress = InMemoryGuideProgress()

    @Test
    fun `starts empty`() {
        assertEquals(emptySet<Int>(), progress.checked("mullvad"))
        assertEquals(emptySet<String>(), progress.idsWithProgress())
    }

    @Test
    fun `setChecked adds and unchecking removes`() {
        progress.setChecked("mullvad", 0, true, total = 6)
        progress.setChecked("mullvad", 3, true, total = 6)
        assertEquals(setOf(0, 3), progress.checked("mullvad"))
        progress.setChecked("mullvad", 0, false, total = 6)
        assertEquals(setOf(3), progress.checked("mullvad"))
    }

    @Test
    fun `out of range indices are never stored`() {
        progress.setChecked("mullvad", -1, true, total = 6)
        progress.setChecked("mullvad", 6, true, total = 6)
        progress.setChecked("mullvad", 99, true, total = 6)
        assertEquals(emptySet<Int>(), progress.checked("mullvad"))
        // even a mixed write keeps only the valid index
        progress.setChecked("mullvad", 2, true, total = 6)
        progress.setChecked("mullvad", 7, true, total = 6)
        assertEquals(setOf(2), progress.checked("mullvad"))
    }

    @Test
    fun `toggle flips on then off`() {
        progress.toggle("tailscale", 1, total = 5)
        assertEquals(setOf(1), progress.checked("tailscale"))
        progress.toggle("tailscale", 1, total = 5)
        assertEquals(emptySet<Int>(), progress.checked("tailscale"))
    }

    @Test
    fun `toggle ignores out of range indices`() {
        progress.toggle("tailscale", -3, total = 5)
        progress.toggle("tailscale", 5, total = 5)
        assertEquals(emptySet<Int>(), progress.checked("tailscale"))
        assertTrue(progress.idsWithProgress().isEmpty())
    }

    @Test
    fun `unchecking the last step forgets the source`() {
        progress.setChecked("openwrt", 0, true, total = 4)
        assertTrue("openwrt" in progress.idsWithProgress())
        progress.setChecked("openwrt", 0, false, total = 4)
        assertFalse("openwrt" in progress.idsWithProgress())
    }

    @Test
    fun `sources are independent`() {
        progress.setChecked("mullvad", 1, true, total = 4)
        progress.setChecked("tailscale", 2, true, total = 4)
        assertEquals(setOf(1), progress.checked("mullvad"))
        assertEquals(setOf(2), progress.checked("tailscale"))
        progress.clear("mullvad")
        assertEquals(emptySet<Int>(), progress.checked("mullvad"))
        assertEquals(setOf(2), progress.checked("tailscale"))
    }

    @Test
    fun `clearAll wipes every source`() {
        progress.setChecked("mullvad", 0, true, total = 4)
        progress.setChecked("openwrt", 1, true, total = 4)
        progress.clearAll()
        assertEquals(emptySet<String>(), progress.idsWithProgress())
    }

    @Test
    fun `idsWithProgress lists exactly the touched sources`() {
        progress.setChecked("a", 0, true, total = 2)
        progress.setChecked("b", 1, true, total = 2)
        progress.setChecked("c", 0, true, total = 2)
        progress.setChecked("b", 1, false, total = 2)
        assertEquals(setOf("a", "c"), progress.idsWithProgress())
    }

    @Test
    fun `bounds and normalize helpers follow one rule`() {
        assertTrue(GuideProgress.isValidIndex(0, total = 5))
        assertTrue(GuideProgress.isValidIndex(4, total = 5))
        assertFalse(GuideProgress.isValidIndex(-1, total = 5))
        assertFalse(GuideProgress.isValidIndex(5, total = 5))
        // normalize repairs a stale set (e.g. after a catalog step removal)
        assertEquals(
            setOf(0, 2),
            GuideProgress.normalize(setOf(0, 2, 9, -4), total = 5)
        )
        assertEquals(emptySet<Int>(), GuideProgress.normalize(setOf(5, 6), total = 5))
    }

    @Test
    fun `same rules as the shared companion — single source of truth`() {
        // the UI calls toggle; the store must behave like setChecked(!checked)
        progress.setChecked("mullvad", 2, true, total = 4)
        progress.toggle("mullvad", 2, total = 4)
        assertEquals(emptySet<Int>(), progress.checked("mullvad"))
        progress.toggle("mullvad", 2, total = 4)
        assertEquals(setOf(2), progress.checked("mullvad"))
    }
}
