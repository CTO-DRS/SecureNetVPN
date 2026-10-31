package com.securenet.vpn.service

import androidx.annotation.StringRes
import com.securenet.vpn.R
import com.securenet.vpn.data.model.ConnectionState

/**
 * v1.8.23 — pure presentation policy behind the status notification
 * («الإشعار في يدك» — the notification obeys the user). Two user-facing
 * choices live here, both persisted as plain ints in SecurePrefs:
 *
 *  - [Style] — how much the shade shows while connected: DETAILED keeps
 *    the v1.8.21 three-line expanded body (plus the v1.8.23 session
 *    average), COMPACT keeps only the one-line data summary, MINIMAL
 *    shows nothing but the title.
 *  - [Lockscreen] — how much of the notification survives the lock
 *    screen: hide the sensitive data (default), show everything, or
 *    hide the whole notification.
 *
 * Plus the v1.8.23 state accent: [accentColorRes] tints the shade icon
 * and action buttons with the state's own status color instead of one
 * static brand hue, so connected / connecting / failed read at a glance.
 *
 * Zero Android framework imports — visibility values mirror
 * android.app.Notification.VISIBILITY_* (plain ints, re-declared locally
 * so the pure layer stays JVM-testable via [NotifStyleTest]).
 */
object NotifStyle {

    /** Local mirror of android.app.Notification.VISIBILITY_* constants. */
    const val VISIBILITY_PRIVATE = 0
    const val VISIBILITY_PUBLIC = 1
    const val VISIBILITY_SECRET = -1

    /** How much the shade shows while connected. */
    enum class Style(val stored: Int) {
        DETAILED(0), COMPACT(1), MINIMAL(2);

        companion object {
            /** Unknown stored values fall back to the detailed default. */
            fun from(stored: Int): Style =
                entries.firstOrNull { it.stored == stored } ?: DETAILED
        }
    }

    /** How much of the notification survives the lock screen. */
    enum class Lockscreen(val stored: Int, val visibility: Int) {
        HIDE_CONTENT(0, VISIBILITY_PRIVATE),
        SHOW_ALL(1, VISIBILITY_PUBLIC),
        HIDE_ALL(2, VISIBILITY_SECRET);

        companion object {
            /** Unknown stored values fall back to the private default. */
            fun from(stored: Int): Lockscreen =
                entries.firstOrNull { it.stored == stored } ?: HIDE_CONTENT
        }
    }

    /** The compact data line under the title — dropped entirely for MINIMAL. */
    fun compactLine(style: Style, rxBytes: Long, txBytes: Long): String? =
        when (style) {
            Style.MINIMAL -> null
            else -> NotifText.sessionLine(rxBytes, txBytes)
        }

    /** The expanded body — DETAILED only; everything else stays collapsed. */
    fun expandedText(style: Style, body: String): String? =
        if (style == Style.DETAILED) body else null

    /** Localized label for a stored style int (settings summary + sheet). */
    @StringRes
    fun styleLabelRes(stored: Int): Int = when (Style.from(stored)) {
        Style.DETAILED -> R.string.notif_style_detailed
        Style.COMPACT -> R.string.notif_style_compact
        Style.MINIMAL -> R.string.notif_style_minimal
    }

    /** Localized label for a stored lockscreen int (settings summary + sheet). */
    @StringRes
    fun lockscreenLabelRes(stored: Int): Int = when (Lockscreen.from(stored)) {
        Lockscreen.HIDE_CONTENT -> R.string.notif_lock_data
        Lockscreen.SHOW_ALL -> R.string.notif_lock_show
        Lockscreen.HIDE_ALL -> R.string.notif_lock_hide
    }

    /**
     * Accent color (R.color status ids) per connection state — green while
     * protected, amber while negotiating, neutral when down, red on failure.
     */
    fun accentColorRes(state: ConnectionState): Int = when (state) {
        ConnectionState.CONNECTED -> R.color.status_connected
        ConnectionState.CONNECTING,
        ConnectionState.REQUESTING_PERMISSION,
        ConnectionState.RECONNECTING -> R.color.status_connecting
        ConnectionState.DISCONNECTING,
        ConnectionState.DISCONNECTED -> R.color.status_disconnected
        ConnectionState.FAILED -> R.color.status_failed
    }
}
