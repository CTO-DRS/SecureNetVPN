package com.securenet.vpn.guide

import android.content.Context
import android.content.SharedPreferences

/**
 * v1.8.15 — per-source setup-wizard progress for the guide's step-by-step
 * tutorials (معالج الإعداد التفاعلي). The catalog ships a tutorial per
 * SECTION ([WireGuideCatalog.stepsRes]); this store tracks which steps the
 * user checked off for a specific SOURCE, so "am I done with this one?"
 * survives app restarts and the detail page can show "3 of 6 steps" with a
 * progress bar.
 *
 * Keys are the source's catalog id ([WireGuideCatalog.entries]); step
 * indices are 0-based positions inside that section's array, and every
 * write is bounds-checked against the array length supplied by the caller
 * — a stale index (e.g. after a catalog change) is silently ignored
 * instead of corrupting the set.
 *
 * These are non-sensitive UI preferences, so a plain SharedPreferences file
 * is used deliberately — the encrypted SecurePrefs ladder stays reserved
 * for keys, credentials and logs. The interface is pure Kotlin so the whole
 * behaviour (bounds checks, toggling, clearing) is unit-testable on the JVM
 * via [InMemoryGuideProgress]; [SharedPrefsGuideProgress] is a thin
 * persistence shell over the exact same rules.
 */
interface GuideProgress {

    /** Checked step indices for [id] (0-based, already bounds-valid). */
    fun checked(id: String): Set<Int>

    /**
     * Sets one step of [id] to [checked]. Out-of-range indices
     * (index < 0 or index >= [total]) are ignored — never stored.
     */
    fun setChecked(id: String, index: Int, checked: Boolean, total: Int)

    /** Flips one step of [id], bounds-checked against [total]. */
    fun toggle(id: String, index: Int, total: Int) =
        setChecked(id, index, index !in checked(id), total)

    /** Drops all progress for [id] (other sources are untouched). */
    fun clear(id: String)

    /** Drops every source's progress (irreversible). */
    fun clearAll()

    /** Ids that currently carry at least one checked step. */
    fun idsWithProgress(): Set<String>

    companion object {

        /** Shared bounds rule: a valid step index lives in 0 until [total]. */
        fun isValidIndex(index: Int, total: Int): Boolean = index in 0 until total

        /**
         * Shared repair rule for read paths: filter a stored set down to
         * indices still valid for the current array length, so a catalog
         * change degrades gracefully instead of painting phantom checks.
         */
        fun normalize(checked: Set<Int>, total: Int): Set<Int> =
            checked.filter { isValidIndex(it, total) }.toSet()
    }
}

/** Pure in-memory implementation — the unit-testable reference behaviour. */
class InMemoryGuideProgress : GuideProgress {

    private val data = mutableMapOf<String, MutableSet<Int>>()

    override fun checked(id: String): Set<Int> = data[id]?.toSet() ?: emptySet()

    override fun setChecked(id: String, index: Int, checked: Boolean, total: Int) {
        if (!GuideProgress.isValidIndex(index, total)) return
        val set = data.getOrPut(id) { mutableSetOf() }
        if (checked) set.add(index) else {
            set.remove(index)
            if (set.isEmpty()) data.remove(id)
        }
    }

    override fun clear(id: String) {
        data.remove(id)
    }

    override fun clearAll() {
        data.clear()
    }

    override fun idsWithProgress(): Set<String> = data.keys.toSet()
}

/**
 * SharedPreferences-backed implementation. One StringSet per source under
 * `steps_<id>` (indices as strings); every write flows through the same
 * bounds rule as the in-memory reference, and every read is re-normalized
 * so stale indices can never surface.
 */
class SharedPrefsGuideProgress(context: Context) : GuideProgress {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun checked(id: String): Set<Int> =
        GuideProgress.normalize(
            prefs.getStringSet(key(id), emptySet())
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet() ?: emptySet(),
            Int.MAX_VALUE // raw read; callers re-normalize against their array
        )

    override fun setChecked(id: String, index: Int, checked: Boolean, total: Int) {
        if (!GuideProgress.isValidIndex(index, total)) return
        val current = prefs.getStringSet(key(id), emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        val next = if (checked) current + index else current - index
        prefs.edit().apply {
            if (next.isEmpty()) remove(key(id))
            else putStringSet(key(id), next.map { it.toString() }.toSet())
        }.apply()
    }

    override fun clear(id: String) {
        prefs.edit().remove(key(id)).apply()
    }

    override fun clearAll() {
        prefs.edit().apply {
            prefs.all.keys.toList()
                .filter { it.startsWith(PREFIX) }
                .forEach { remove(it) }
        }.apply()
    }

    override fun idsWithProgress(): Set<String> =
        prefs.all.keys.mapNotNull { k ->
            if (k.startsWith(PREFIX)) k.removePrefix(PREFIX).takeIf { it.isNotBlank() }
            else null
        }.toSet()

    private fun key(id: String) = PREFIX + id

    private companion object {
        const val PREFS_FILE = "guide_setup_progress"
        const val PREFIX = "steps_"
    }
}

/** Process-wide handle so the detail page and future surfaces share progress. */
object GuideProgressProvider {

    @Volatile
    private var instance: GuideProgress? = null

    fun get(context: Context): GuideProgress = instance ?: synchronized(this) {
        instance ?: SharedPrefsGuideProgress(context.applicationContext).also { instance = it }
    }
}
