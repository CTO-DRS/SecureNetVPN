package com.securenet.vpn

import com.securenet.vpn.data.local.PrefsRecoveryLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.6.1 — Tests for the encrypted-prefs recovery ladder that fixes the
 * keystore crash loop ("SecureNet VPN has stopped" on every launch on
 * devices with a corrupt/invalidated AndroidKeyStore or restored prefs).
 *
 * The ladder is generic, so plain JVM sentinels stand in for
 * SharedPreferences instances — fully framework-free.
 */
class PrefsRecoveryLadderTest {

    private class Vault(val name: String)

    // ------------------------------------------------------------------
    // 1. Healthy device: fresh encrypted creation succeeds; the destructive
    //    purge path is never touched.
    // ------------------------------------------------------------------
    @Test
    fun freshSuccessReturnsEncryptedPrefsAndNeverPurges() {
        val encrypted = Vault("encrypted")
        var purgeInvoked = false
        var fallbackInvoked = false

        val resolved = PrefsRecoveryLadder.resolve(
            attemptFresh = { encrypted },
            recover = {
                purgeInvoked = true
                Vault("recovered")
            },
            fallback = {
                fallbackInvoked = true
                Vault("plain")
            }
        )

        assertSame(encrypted, resolved)
        assertTrue(!purgeInvoked)
        assertTrue(!fallbackInvoked)
    }

    // ------------------------------------------------------------------
    // 2. Corrupt keystore blob: fresh attempt fails (null after catching),
    //    the purge + retry succeeds — the classic restore/clone recovery.
    // ------------------------------------------------------------------
    @Test
    fun freshFailureFallsThroughToPurgeAndRetry() {
        var freshAttempts = 0
        var purgeInvoked = false
        val recovered = Vault("recovered")

        val resolved = PrefsRecoveryLadder.resolve(
            attemptFresh = {
                freshAttempts++
                null // EncryptedSharedPreferences threw (AEADBadTag etc.)
            },
            recover = {
                purgeInvoked = true
                recovered
            },
            fallback = { Vault("plain") }
        )

        assertSame(recovered, resolved)
        assertEquals(1, freshAttempts)
        assertTrue(purgeInvoked)
    }

    // ------------------------------------------------------------------
    // 3. Fundamentally broken keystore (some OEM devices): both encrypted
    //    attempts fail, plain prefs keep the app usable — no crash loop.
    // ------------------------------------------------------------------
    @Test
    fun bothEncryptedAttemptsFailingUsesPlainFallback() {
        var attempts = 0
        val plain = Vault("plain")

        val resolved = PrefsRecoveryLadder.resolve(
            attemptFresh = { attempts++; null },
            recover = { attempts++; null },
            fallback = { plain }
        )

        assertSame(plain, resolved)
        assertEquals(2, attempts)
    }

    // ------------------------------------------------------------------
    // 4. Ordering is strictly fresh -> recover -> fallback.
    // ------------------------------------------------------------------
    @Test
    fun orderingIsFreshThenRecoverThenFallback() {
        val order = mutableListOf<String>()

        PrefsRecoveryLadder.resolve(
            attemptFresh = { order.add("fresh"); null },
            recover = { order.add("recover"); null },
            fallback = { order.add("fallback"); Vault("plain") }
        )

        assertEquals(listOf("fresh", "recover", "fallback"), order)
    }

    // ------------------------------------------------------------------
    // 5. First-success-wins semantics: a non-null fresh result short-circuits
    //    even if later stages would also work.
    // ------------------------------------------------------------------
    @Test
    fun nonNullFreshResultShortCircuitsLaterStages() {
        val encrypted = Vault("encrypted")

        val resolved = PrefsRecoveryLadder.resolve(
            attemptFresh = { encrypted },
            recover = { throw IllegalStateException("must not be called") },
            fallback = { throw IllegalStateException("must not be called") }
        )

        assertSame(encrypted, resolved)
    }
}
