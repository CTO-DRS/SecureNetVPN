package com.securenet.vpn.intelligence

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Network anomaly detection, 100% on-device.
 *
 * Two detectors:
 *
 *  1. Latency watchdog — exponentially weighted moving average (EWMA) plus a
 *     z-score gate. Samples far above the rolling baseline are flagged so the
 *     optimizer can trigger MTU re-probing or server failover.
 *
 *  2. DNS integrity — compares resolved addresses of security-critical
 *     domains against a pin table of expected network prefixes. A lookup that
 *     lands outside the expected space is the classic hijack/censorship
 *     fingerprint and is reported before any credentials are exchanged.
 *
 * Pure Kotlin, clock-free math -> unit-testable on the JVM.
 */
class NetworkAnomalyDetector(
    private val alpha: Double = 0.30,
    private val zThreshold: Double = 3.0,
    private val minSamples: Int = 5
) {

    data class LatencyAnomaly(
        val sampleMs: Double,
        val baselineMs: Double,
        val zScore: Double
    )

    /** Incremental EWMA state; feed every latency sample you observe. */
    inner class LatencyWatchdog {

        private var mean: Double = Double.NaN
        private var variance: Double = 0.0
        private var count: Int = 0

        /**
         * Feed the next sample. Returns an anomaly when the sample deviates
         * from the EWMA baseline by more than zThreshold standard deviations.
         * The z-score is judged against the PRIOR variance so a long, quiet
         * stretch cannot hide a genuine spike (classic EWMA pitfall).
         */
        fun observe(sampleMs: Double): LatencyAnomaly? {
            if (sampleMs.isNaN() || sampleMs < 0) return null
            if (mean.isNaN()) {
                mean = sampleMs
                variance = 0.0
                count = 1
                return null
            }
            count++
            val delta = sampleMs - mean
            val priorStd = sqrt(variance).coerceAtLeast(1.0)
            val z = delta / priorStd
            val baselineBefore = mean
            variance = alpha * (delta * delta) + (1 - alpha) * variance
            mean += alpha * delta

            if (count < minSamples) return null
            return if (z >= zThreshold) {
                LatencyAnomaly(sampleMs = sampleMs, baselineMs = baselineBefore, zScore = z)
            } else null
        }

        fun baselineMs(): Double = if (mean.isNaN()) 0.0 else mean
        fun sampleCount(): Int = count
    }

    enum class DnsVerdict { TRUSTED, SUSPICIOUS, UNKNOWN_DOMAIN }

    data class DnsIntelligence(
        val hostname: String,
        val resolvedIp: String,
        val verdict: DnsVerdict,
        val reason: String
    )

    /**
     * Judge a resolved address against the embedded expected-prefix table.
     * Only high-value domains are listed; everything else returns
     * UNKNOWN_DOMAIN (no opinion, no noise).
     */
    fun checkDns(hostname: String, resolvedIp: String): DnsIntelligence {
        val prefixes = TRUSTED_DNS_PREFIXES[hostname.lowercase().trimEnd('.')]
            ?: return DnsIntelligence(hostname, resolvedIp, DnsVerdict.UNKNOWN_DOMAIN,
                "domain not in pin table")
        val trusted = prefixes.any { resolvedIp.startsWith(it) }
        return if (trusted) {
            DnsIntelligence(hostname, resolvedIp, DnsVerdict.TRUSTED, "within expected prefix")
        } else {
            DnsIntelligence(hostname, resolvedIp, DnsVerdict.SUSPICIOUS,
                "resolved address outside known prefix - possible DNS hijack")
        }
    }

    companion object {
        /**
         * Expected address prefixes for well-known privacy infrastructure.
         * Deliberately coarse (class-level) to survive legitimate round-robin
         * rotation while still catching whole-subnet hijacks.
         */
        internal val TRUSTED_DNS_PREFIXES: Map<String, List<String>> = mapOf(
            "cloudflare-dns.com" to listOf("1.1.1.", "1.0.0.", "162.159."),
            "dns.google" to listOf("8.8.8.", "8.8.4.", "2001:4860:4860:"),
            "one.one.one.one" to listOf("1.1.1.", "1.0.0."),
            "dns.quad9.net" to listOf("9.9.9.", "149.112."),
            "dns.adguard.com" to listOf("94.140.", "2a10:")
        )

        /** Convenience: symmetric absolute deviation helper for tests. */
        internal fun deviation(a: Double, b: Double): Double = abs(a - b)
    }
}
