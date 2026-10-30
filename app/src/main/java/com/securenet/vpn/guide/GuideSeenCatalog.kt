package com.securenet.vpn.guide

import android.content.Context
import android.content.SharedPreferences

/**
 * v1.8.16 — which catalog sources the user has already been told about.
 * The guide's source list keeps growing (v1.8.12 took it to 69), so the
 * screen now greets fresh additions with a dismissible "new sources"
 * banner and a 🆕 NEW badge on the affected cards (تنبيه عند إضافة مصادر
 * جديدة). This store remembers the ids the user has already been shown so
 * an alert fires exactly once per update: when the user taps "got it"
 * (and on the very first run, silently — no shouting on day one) the
 * current catalog snapshot is recorded here.
 *
 * Ids are the catalog ids ([WireGuideCatalog.entries]); ids that later
 * disappear from the catalog simply stop matching — nothing to clean up.
 *
 * Non-sensitive UI preference, so a plain SharedPreferences file is used
 * deliberately (same rationale as [GuideStore]); the interface is pure
 * Kotlin so the diff rules are unit-testable on the JVM via
 * [InMemoryGuideSeenCatalog]; [SharedPrefsGuideSeenCatalog] is a thin
 * persistence shell over the exact same rules.
 */
interface GuideSeenCatalog {

    /** Ids already acknowledged (may contain ids no longer in the catalog). */
    fun seenIds(): Set<String>

    /** Adds [ids] to the acknowledged set (existing entries are kept). */
    fun markAllSeen(ids: Collection<String>)

    companion object {

        /**
         * Ids of [currentIds] never acknowledged before, in catalog order.
         * An empty seen set is the first-run baseline and deliberately
         * yields nothing: the very first snapshot is recorded silently so
         * every LATER update can be announced honestly.
         */
        fun unseen(currentIds: List<String>, seen: Set<String>): List<String> =
            if (seen.isEmpty()) emptyList() else currentIds.filter { it !in seen }

        /** Display names for [ids], in catalog order (unknown ids skipped). */
        fun namesOf(current: List<GuideEntry>, ids: Collection<String>): List<String> =
            current.filter { it.id in ids }.map { it.name }
    }
}

/** Pure in-memory implementation — the unit-testable reference behaviour. */
class InMemoryGuideSeenCatalog : GuideSeenCatalog {

    private val seen = mutableSetOf<String>()

    override fun seenIds(): Set<String> = seen.toSet()

    override fun markAllSeen(ids: Collection<String>) {
        seen.addAll(ids)
    }
}

/** SharedPreferences-backed shell: one StringSet under `seen_ids`. */
class SharedPrefsGuideSeenCatalog(context: Context) : GuideSeenCatalog {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun seenIds(): Set<String> =
        prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    override fun markAllSeen(ids: Collection<String>) {
        prefs.edit().putStringSet(KEY, seenIds() + ids).apply()
    }

    private companion object {
        const val PREFS_FILE = "guide_seen_sources"
        const val KEY = "seen_ids"
    }
}

/** Process-wide handle so every visit to the guide screen shares one log. */
object GuideSeenCatalogProvider {

    @Volatile
    private var instance: GuideSeenCatalog? = null

    fun get(context: Context): GuideSeenCatalog = instance ?: synchronized(this) {
        instance ?: SharedPrefsGuideSeenCatalog(context.applicationContext).also { instance = it }
    }
}
