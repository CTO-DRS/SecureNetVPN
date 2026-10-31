package com.securenet.vpn

import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.service.NotifStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.8.23 — the pure presentation policy behind «الإشعار في يدك».
 * The user chooses how much the shade shows ([NotifStyle.Style]) and what
 * survives the lock screen ([NotifStyle.Lockscreen]); both choices are
 * stored as plain ints, so the resolvers must clamp unknown values to the
 * safe defaults and the visibility ints must mirror android.app.Notification
 * exactly (PRIVATE=0, PUBLIC=1, SECRET=-1).
 */
class NotifStyleTest {

    @Test
    fun `style roundtrips stored values and clamps unknowns to detailed`() {
        assertEquals(NotifStyle.Style.DETAILED, NotifStyle.Style.from(0))
        assertEquals(NotifStyle.Style.COMPACT, NotifStyle.Style.from(1))
        assertEquals(NotifStyle.Style.MINIMAL, NotifStyle.Style.from(2))
        assertEquals(NotifStyle.Style.DETAILED, NotifStyle.Style.from(99))
        assertEquals(NotifStyle.Style.DETAILED, NotifStyle.Style.from(-3))
        assertEquals(0, NotifStyle.Style.DETAILED.stored)
        assertEquals(1, NotifStyle.Style.COMPACT.stored)
        assertEquals(2, NotifStyle.Style.MINIMAL.stored)
    }

    @Test
    fun `lockscreen maps to the exact Notification visibility ints and clamps`() {
        // Mirrors android.app.Notification: PRIVATE=0, PUBLIC=1, SECRET=-1.
        assertEquals(0, NotifStyle.VISIBILITY_PRIVATE)
        assertEquals(1, NotifStyle.VISIBILITY_PUBLIC)
        assertEquals(-1, NotifStyle.VISIBILITY_SECRET)
        assertEquals(NotifStyle.VISIBILITY_PRIVATE, NotifStyle.Lockscreen.from(0).visibility)
        assertEquals(NotifStyle.VISIBILITY_PUBLIC, NotifStyle.Lockscreen.from(1).visibility)
        assertEquals(NotifStyle.VISIBILITY_SECRET, NotifStyle.Lockscreen.from(2).visibility)
        assertEquals(NotifStyle.Lockscreen.HIDE_CONTENT, NotifStyle.Lockscreen.from(42))
    }

    @Test
    fun `compact line drops only for the minimal style`() {
        val line = "↓ 1.2 MB · ↑ 340 KB"
        assertEquals(line, NotifStyle.compactLine(NotifStyle.Style.DETAILED, 1_258_291L, 348_160L))
        assertEquals(line, NotifStyle.compactLine(NotifStyle.Style.COMPACT, 1_258_291L, 348_160L))
        assertNull(NotifStyle.compactLine(NotifStyle.Style.MINIMAL, 1_258_291L, 348_160L))
    }

    @Test
    fun `expanded text is gated to the detailed style`() {
        assertEquals("body", NotifStyle.expandedText(NotifStyle.Style.DETAILED, "body"))
        assertNull(NotifStyle.expandedText(NotifStyle.Style.COMPACT, "body"))
        assertNull(NotifStyle.expandedText(NotifStyle.Style.MINIMAL, "body"))
    }

    @Test
    fun `accent colors map each state to its own status color`() {
        assertEquals(R.color.status_connected, NotifStyle.accentColorRes(ConnectionState.CONNECTED))
        assertEquals(
            R.color.status_connecting,
            NotifStyle.accentColorRes(ConnectionState.CONNECTING)
        )
        assertEquals(
            R.color.status_connecting,
            NotifStyle.accentColorRes(ConnectionState.RECONNECTING)
        )
        assertEquals(
            R.color.status_connecting,
            NotifStyle.accentColorRes(ConnectionState.REQUESTING_PERMISSION)
        )
        assertEquals(
            R.color.status_disconnected,
            NotifStyle.accentColorRes(ConnectionState.DISCONNECTED)
        )
        assertEquals(
            R.color.status_disconnected,
            NotifStyle.accentColorRes(ConnectionState.DISCONNECTING)
        )
        assertEquals(R.color.status_failed, NotifStyle.accentColorRes(ConnectionState.FAILED))
        // The four families must stay visually distinct resources.
        assertEquals(
            4,
            setOf(
                R.color.status_connected,
                R.color.status_connecting,
                R.color.status_disconnected,
                R.color.status_failed
            ).size
        )
    }

    @Test
    fun `label resources resolve for every stored int`() {
        assertEquals(R.string.notif_style_detailed, NotifStyle.styleLabelRes(0))
        assertEquals(R.string.notif_style_compact, NotifStyle.styleLabelRes(1))
        assertEquals(R.string.notif_style_minimal, NotifStyle.styleLabelRes(2))
        assertEquals(R.string.notif_style_detailed, NotifStyle.styleLabelRes(77))
        assertEquals(R.string.notif_lock_data, NotifStyle.lockscreenLabelRes(0))
        assertEquals(R.string.notif_lock_show, NotifStyle.lockscreenLabelRes(1))
        assertEquals(R.string.notif_lock_hide, NotifStyle.lockscreenLabelRes(2))
        assertEquals(R.string.notif_lock_data, NotifStyle.lockscreenLabelRes(-9))
        // Labels and descriptions all ship in the resources — never blank ids.
        listOf(
            R.string.notif_style_detailed_desc,
            R.string.notif_style_compact_desc,
            R.string.notif_style_minimal_desc,
            R.string.notif_lock_data_desc,
            R.string.notif_lock_show_desc,
            R.string.notif_lock_hide_desc
        ).forEach { assertNotNull(it) }
    }
}
