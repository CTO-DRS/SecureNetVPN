package com.securenet.vpn.security

import androidx.annotation.StringRes
import com.securenet.vpn.R

/**
 * v1.8.24 — pure policy behind the app lock («قفل التطبيق»): when the
 * user enables it, opening SecureNet demands the device's own screen
 * lock (fingerprint or PIN) before any dashboard content is visible.
 * The gate is implemented in MainActivity as an opaque overlay plus a
 * platform-BiometricPrompt round trip; everything DECIDABLE off the UI
 * lives here so it stays JVM-testable.
 *
 * Semantics of [shouldLock]:
 *  - the feature disabled never locks (the switch is the master kill);
 *  - [lastPausedAt] <= 0 means "no backgrounding was ever recorded" —
 *    a fresh install or the very first open after enabling never locks,
 *    because the user is by definition inside the app turning it on;
 *  - the grace window ([Timeout.delayMillis]) must have fully elapsed —
 *    IMMEDIATE means any recorded backgrounding re-locks;
 *  - a clock that jumped backwards relative to the recorded pause never
 *    locks (defensive: never trap the user in a lock they can reason
 *    their way out of by fixing the time).
 *
 * Zero Android framework imports beyond @StringRes/R (labels only) —
 * fully JVM-testable via [com.securenet.vpn.AppLockPolicyTest].
 */
object AppLockPolicy {

    /** Grace window between leaving the app and the next lock. */
    enum class Timeout(val stored: Int, val delayMillis: Long) {
        IMMEDIATE(0, 0L),
        ONE_MIN(1, 60_000L),
        FIVE_MIN(2, 300_000L);

        companion object {
            /** Unknown stored values fall back to the strictest choice. */
            fun from(stored: Int): Timeout =
                entries.firstOrNull { it.stored == stored } ?: IMMEDIATE
        }
    }

    fun shouldLock(
        enabled: Boolean,
        timeout: Timeout,
        lastPausedAt: Long,
        nowMillis: Long
    ): Boolean {
        if (!enabled) return false
        if (lastPausedAt <= 0L) return false
        if (nowMillis < lastPausedAt) return false
        return nowMillis - lastPausedAt >= timeout.delayMillis
    }

    /** Localized label for a stored timeout (settings row + sheet). */
    @StringRes
    fun timeoutLabelRes(stored: Int): Int = when (Timeout.from(stored)) {
        Timeout.IMMEDIATE -> R.string.applock_timeout_immediate
        Timeout.ONE_MIN -> R.string.applock_timeout_1min
        Timeout.FIVE_MIN -> R.string.applock_timeout_5min
    }
}
