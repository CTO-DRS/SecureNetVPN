package com.securenet.vpn.data.local

/**
 * v1.6.1 — Pure decision logic for the encrypted-preferences recovery ladder.
 *
 * Some devices end up with an AndroidKeyStore master key that can no longer
 * decrypt the preferences file (OEM keystore bugs, key invalidation after a
 * system update, restored/cloned app data where the hardware-bound key does
 * not travel with the backup). When that happens the previous behaviour
 * threw from Application onCreate and produced a permanent crash loop —
 * "SecureNet VPN has stopped" on every launch.
 *
 * The ladder never throws and never returns null:
 * 1. attempt a fresh encrypted creation;
 * 2. on any failure let the caller purge the corrupt state (stale keystore
 *    alias + undecryptable prefs file) and retry — settings reset once,
 *    which is strictly better than a crash loop;
 * 3. if encryption is fundamentally broken on the device, fall back to a
 *    plain SharedPreferences file so the app stays usable.
 *
 * Generic over the result type so the decision ordering is unit-testable on
 * the JVM with trivial fakes (no Android runtime, no Robolectric). [SecurePrefs]
 * instantiates it with T = [android.content.SharedPreferences].
 */
object PrefsRecoveryLadder {

    fun <T> resolve(
        attemptFresh: () -> T?,
        recover: () -> T?,
        fallback: () -> T
    ): T {
        attemptFresh()?.let { return it }
        recover()?.let { return it }
        return fallback()
    }
}
