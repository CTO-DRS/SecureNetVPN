package com.securenet.vpn.guide

import android.content.Context
import android.content.SharedPreferences

/**
 * v1.8.1 — per-source user signals for the WireGuard config-sources guide:
 *  - a 1–5 star rating (0 = unrated),
 *  - a favorite/bookmark flag,
 *  - an optional free-text personal note (e.g. "my server — best at night").
 *
 * Stored under the source's catalog id (see [WireGuideCatalog.entries]), so
 * signals survive app restarts and travel with the catalog.
 *
 * These are non-sensitive UI preferences, so a plain SharedPreferences file
 * is used deliberately — the encrypted SecurePrefs ladder stays reserved for
 * keys, credentials and logs. The interface is pure Kotlin so the whole
 * behaviour (clamping, trimming, toggling) is unit-testable on the JVM via
 * [InMemoryGuideStore]; [SharedPrefsGuideStore] is a thin persistence shell
 * over the exact same rules.
 */
interface GuideStore {

    /** Saved stars for [id]: 0 (unrated) or 1..5. */
    fun rating(id: String): Int

    /** Sets stars for [id]; values < 1 clear the rating, values > 5 clamp to 5. */
    fun setRating(id: String, stars: Int)

    /** True when the user bookmarked [id]. */
    fun isFavorite(id: String): Boolean

    /** Adds/removes the bookmark for [id]. */
    fun setFavorite(id: String, favorite: Boolean)

    /** Ids currently bookmarked (for the favorites filter chip). */
    fun favoriteIds(): Set<String>

    /** The saved personal note for [id], or null when none/blank. */
    fun note(id: String): String?

    /** Saves [text] as the note for [id]; null or blank clears it. */
    fun setNote(id: String, text: String?)

    /** Ids that currently carry a non-blank personal note. */
    fun noteIds(): Set<String>

    /** Ids that currently carry a 1–5 star rating (v1.8.2, backup export). */
    fun ratedIds(): Set<String>

    companion object {
        /** Shared clamping/normalisation rule for ratings. */
        fun normalizeRating(stars: Int): Int = when {
            stars <= 0 -> 0
            else -> stars.coerceAtMost(5)
        }

        /** Shared normalisation rule for notes: trim; blank means "remove". */
        fun normalizeNote(text: String?): String? =
            text?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** Pure in-memory implementation — the unit-testable reference behaviour. */
class InMemoryGuideStore : GuideStore {

    private val ratings = mutableMapOf<String, Int>()
    private val favorites = mutableMapOf<String, Boolean>()
    private val notes = mutableMapOf<String, String>()

    override fun rating(id: String): Int = ratings[id] ?: 0

    override fun setRating(id: String, stars: Int) {
        val value = GuideStore.normalizeRating(stars)
        if (value == 0) ratings.remove(id) else ratings[id] = value
    }

    override fun isFavorite(id: String): Boolean = favorites[id] ?: false

    override fun setFavorite(id: String, favorite: Boolean) {
        if (favorite) favorites[id] = true else favorites.remove(id)
    }

    override fun favoriteIds(): Set<String> = favorites.keys.toSet()

    override fun note(id: String): String? = notes[id]

    override fun setNote(id: String, text: String?) {
        val value = GuideStore.normalizeNote(text)
        if (value == null) notes.remove(id) else notes[id] = value
    }

    override fun noteIds(): Set<String> = notes.keys.toSet()

    override fun ratedIds(): Set<String> = ratings.keys.toSet()
}

/**
 * SharedPreferences-backed implementation. All validation flows through the
 * [GuideStore] companion normalisers so the stored data matches the in-memory
 * reference exactly. Keys are prefixed so the file stays self-describing:
 * `rating_<id>`, `fav_<id>`, `note_<id>`.
 */
class SharedPrefsGuideStore(context: Context) : GuideStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun rating(id: String): Int = prefs.getInt(key(PREFIX_RATING, id), 0)

    override fun setRating(id: String, stars: Int) {
        val value = GuideStore.normalizeRating(stars)
        prefs.edit().apply {
            if (value == 0) remove(key(PREFIX_RATING, id)) else putInt(key(PREFIX_RATING, id), value)
        }.apply()
    }

    override fun isFavorite(id: String): Boolean = prefs.getBoolean(key(PREFIX_FAV, id), false)

    override fun setFavorite(id: String, favorite: Boolean) {
        prefs.edit().apply {
            if (favorite) putBoolean(key(PREFIX_FAV, id), true)
            else remove(key(PREFIX_FAV, id))
        }.apply()
    }

    override fun favoriteIds(): Set<String> = idsForPrefix(PREFIX_FAV)

    override fun note(id: String): String? = prefs.getString(key(PREFIX_NOTE, id), null)

    override fun setNote(id: String, text: String?) {
        val value = GuideStore.normalizeNote(text)
        prefs.edit().apply {
            if (value == null) remove(key(PREFIX_NOTE, id)) else putString(key(PREFIX_NOTE, id), value)
        }.apply()
    }

    override fun noteIds(): Set<String> = idsForPrefix(PREFIX_NOTE)

    override fun ratedIds(): Set<String> = idsForPrefix(PREFIX_RATING)

    private fun idsForPrefix(prefix: String): Set<String> =
        prefs.all.keys.mapNotNull { k ->
            if (k.startsWith(prefix)) k.removePrefix(prefix).takeIf { it.isNotBlank() } else null
        }.toSet()

    private fun key(prefix: String, id: String) = prefix + id

    private companion object {
        const val PREFS_FILE = "guide_user_signals"
        const val PREFIX_RATING = "rating_"
        const val PREFIX_FAV = "fav_"
        const val PREFIX_NOTE = "note_"
    }
}

/** Process-wide handle so the guide screen and the quick sheet share signals. */
object GuideStoreProvider {

    @Volatile
    private var instance: GuideStore? = null

    fun get(context: Context): GuideStore = instance ?: synchronized(this) {
        instance ?: SharedPrefsGuideStore(context.applicationContext).also { instance = it }
    }
}
