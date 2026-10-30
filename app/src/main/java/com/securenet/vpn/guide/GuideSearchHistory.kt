package com.securenet.vpn.guide

import android.content.Context
import android.content.SharedPreferences

/**
 * v1.8.20 — recent-search history for the guide's search field (سجل البحث
 * الحديث). The 69-source catalog is searched across names, URLs, EN/AR
 * keywords and the user's own notes, so a query like "مجاني" or "mesh" is
 * worth re-running: every confirmed search (the keyboard's search action,
 * or tapping a history chip) is remembered most-recent-first, duplicates
 * collapse into one entry that jumps back to the front, and the list keeps
 * at most [MAX_ENTRIES] queries.
 *
 * Pure Kotlin core — [record], [encode] and [decode] are plain functions
 * over strings and lists with zero Android imports, fully unit-testable on
 * the JVM ([GuideSearchHistoryTest]); [SharedPrefsGuideSearchHistory] is a
 * thin persistence shell following the same split as [GuideStore].
 *
 * Storage format: the queries joined by the ASCII unit separator (\u001F)
 * inside a plain SharedPreferences file — queries never contain control
 * characters (the search field is single-line text), so the separator can
 * never collide with real content.
 */
object GuideSearchHistory {

    /** How many recent queries are kept (oldest dropped beyond this). */
    const val MAX_ENTRIES = 8

    /** Character that separates the stored queries inside the prefs string. */
    const val UNIT_SEPARATOR = '\u001F'

    /** SharedPreferences file backing [SharedPrefsGuideSearchHistory]. */
    const val PREFS_FILE = "guide_search_history"

    /**
     * Records [rawQuery] into [history]: trimmed, blank queries ignored,
     * case-insensitive duplicates collapse (the freshly typed spelling
     * wins), newest first, capped at [MAX_ENTRIES]. Returns the new list;
     * the input list is never mutated.
     */
    fun record(history: List<String>, rawQuery: String): List<String> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return history
        val rest = history.filterNot { it.equals(query, ignoreCase = true) }
        return (listOf(query) + rest).take(MAX_ENTRIES)
    }

    /** Serialises [history] for storage (single string, separator-joined). */
    fun encode(history: List<String>): String =
        history.joinToString(UNIT_SEPARATOR.toString())

    /**
     * Parses a stored string back into the query list: null, blank and
     * separator-only values all decode to an empty history, and any entry
     * that somehow turned blank after a round-trip is dropped.
     */
    fun decode(stored: String?): List<String> =
        stored?.split(UNIT_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
}

/**
 * SharedPreferences-backed persistence for [GuideSearchHistory] — reads
 * and writes always flow through the pure encode/decode/record rules so
 * the stored data matches the in-memory reference exactly.
 */
class SharedPrefsGuideSearchHistory(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            GuideSearchHistory.PREFS_FILE, Context.MODE_PRIVATE
        )

    /** The current history, newest first (empty on a cold start). */
    fun history(): List<String> =
        GuideSearchHistory.decode(prefs.getString(PREFS_KEY, null))

    /** Records [rawQuery] through the pure [GuideSearchHistory.record] rule. */
    fun record(rawQuery: String) {
        val updated = GuideSearchHistory.record(history(), rawQuery)
        prefs.edit().putString(PREFS_KEY, GuideSearchHistory.encode(updated)).apply()
    }

    /** v1.8.20 — wipes the whole history (the "Clear" chip on the row). */
    fun clear() {
        prefs.edit().remove(PREFS_KEY).apply()
    }

    private companion object {
        const val PREFS_KEY = "recent_queries"
    }
}

/** Process-wide handle so the guide screen and future screens share one history. */
object GuideSearchHistoryProvider {

    @Volatile
    private var instance: SharedPrefsGuideSearchHistory? = null

    fun get(context: Context): SharedPrefsGuideSearchHistory =
        instance ?: synchronized(this) {
            instance ?: SharedPrefsGuideSearchHistory(context.applicationContext)
                .also { instance = it }
        }
}
