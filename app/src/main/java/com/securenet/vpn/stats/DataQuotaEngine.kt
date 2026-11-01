package com.securenet.vpn.stats

import androidx.annotation.StringRes
import com.securenet.vpn.R

/**
 * v1.8.24 — pure policy behind the monthly data-quota alert
 * («حصة البيانات الشهرية» — the user names a per-month cap, the app
 * watches the real traffic already recorded in the encrypted session
 * log and raises its own alert when the cap approaches or breaks).
 *
 * The ladder is deliberately three steps: NONE below the warn line,
 * WARN at the warn percent (default 80%) and EXCEEDED at 100%. An alert
 * is warranted only when the ladder ADVANCES ([shouldAlert]) — a level
 * that stays put or regresses never re-notifies, so the user hears one
 * heads-up per threshold crossing, not a nag. Persisting the current
 * level after every evaluation ([Level.stored]) is what re-arms the
 * ladder: a new month, a raised cap or cleared logs drop the level back
 * to NONE and the next crossing alerts again.
 *
 * Zero Android framework imports beyond @StringRes/R (labels only) —
 * fully JVM-testable via [com.securenet.vpn.DataQuotaEngineTest].
 */
object DataQuotaEngine {

    /** Default warn line: 80% of the cap. */
    const val DEFAULT_WARN_PERCENT = 80

    /** Alert ladder; [stored] is the persisted previous-level marker. */
    enum class Level(val stored: Int) {
        NONE(0), WARN(1), EXCEEDED(2);

        companion object {
            /** Unknown stored values degrade to NONE (never re-alert). */
            fun from(stored: Int): Level =
                entries.firstOrNull { it.stored == stored } ?: NONE
        }
    }

    /**
     * One evaluation snapshot: [level] drives the alert, [percent] and
     * [remainingBytes] drive its human-readable body.
     */
    data class QuotaState(
        val level: Level,
        val usedBytes: Long,
        val quotaBytes: Long,
        /** 0..100 display percent, floored and capped — 150% reads as 100%. */
        val percent: Int,
        /** Cap minus usage, clamped at zero once the cap is exceeded. */
        val remainingBytes: Long
    )

    /**
     * Evaluates [usedBytes] against [quotaBytes]. A non-positive cap means
     * the feature is off: level NONE with a zero percent, whatever the usage.
     */
    fun evaluate(
        usedBytes: Long,
        quotaBytes: Long,
        warnPercent: Int = DEFAULT_WARN_PERCENT
    ): QuotaState {
        if (quotaBytes <= 0L) {
            return QuotaState(Level.NONE, usedBytes.coerceAtLeast(0L), 0L, 0, 0L)
        }
        val used = usedBytes.coerceAtLeast(0L)
        val percent = ((used * 100L) / quotaBytes).toInt().coerceIn(0, 100)
        val warnLine = warnPercent.coerceIn(1, 100)
        val level = when {
            used >= quotaBytes -> Level.EXCEEDED
            percent >= warnLine -> Level.WARN
            else -> Level.NONE
        }
        return QuotaState(
            level = level,
            usedBytes = used,
            quotaBytes = quotaBytes,
            percent = percent,
            remainingBytes = (quotaBytes - used).coerceAtLeast(0L)
        )
    }

    /**
     * True only on an advance (NONE → WARN, WARN → EXCEEDED, or a jump
     * straight to EXCEEDED from a persisted NONE). Same-level repeats and
     * regressions are silent by design.
     */
    fun shouldAlert(previousStored: Int, current: Level): Boolean =
        current != Level.NONE && current.stored > previousStored

    /** The cap presets the settings sheet offers (0 bytes = off). */
    enum class Preset(val stored: Int, val bytes: Long) {
        OFF(0, 0L),
        GB1(1, 1L shl 30),
        GB5(2, 5L shl 30),
        GB10(3, 10L shl 30),
        GB50(4, 50L shl 30);

        companion object {
            /** Maps a stored cap back to its preset; anything custom reads OFF. */
            fun fromBytes(bytes: Long): Preset =
                entries.firstOrNull { it.bytes == bytes } ?: OFF
        }
    }

    /** Localized label for the row summary from a stored cap value. */
    @StringRes
    fun presetLabelRes(bytes: Long): Int = when (Preset.fromBytes(bytes)) {
        Preset.OFF -> R.string.quota_off
        Preset.GB1 -> R.string.quota_preset_1gb
        Preset.GB5 -> R.string.quota_preset_5gb
        Preset.GB10 -> R.string.quota_preset_10gb
        Preset.GB50 -> R.string.quota_preset_50gb
    }
}
