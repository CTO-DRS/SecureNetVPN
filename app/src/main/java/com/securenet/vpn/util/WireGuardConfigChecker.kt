package com.securenet.vpn.util

import java.util.Locale

/**
 * v1.8.16 — offline WireGuard configuration checker (🛡️ فاحص إعدادات
 * WireGuard). The user pastes or imports a wg-quick style .conf and gets a
 * line-anchored list of problems BEFORE importing it into SecureNet —
 * everything runs locally, nothing leaves the device.
 *
 * The checker complements [ConfigImporter] (the import path validates to
 * accept/reject); this one explains: every issue carries a severity, a
 * stable code the UI maps to localized text, the offending line number and
 * the exact token that tripped the rule.
 *
 * Rules, in the voice of "what actually breaks a tunnel":
 *  - structure: at least one [Interface], keys inside a section, no stray
 *    or unparsable lines, no duplicated keys;
 *  - identity: PrivateKey / PublicKey / PresharedKey must be valid 32-byte
 *    base64 (validated with [KeyUtils.isValidBase64Key], the same rule the
 *    connection path enforces);
 *  - routing: Address / AllowedIPs must be IPv4/IPv6 addresses or CIDRs;
 *    a peer without AllowedIPs routes nothing; a peer without Endpoint is
 *    unreachable from a client;
 *  - numbers: MTU 576–65535 (warn under 1280), ListenPort and
 *    PersistentKeepalive 0–65535;
 *  - surprises: unknown keys warn (typos are the classic silent killer)
 *    and AllowedIPs = 0.0.0.0/0 or ::/0 is reported as an INFO — full
 *    tunnel is a legitimate choice the user should be making knowingly.
 *
 * No Android APIs are touched, so the whole rule set is unit-testable on
 * the JVM.
 */
object WireGuardConfigChecker {

    enum class Severity { ERROR, WARNING, INFO }

    data class Issue(
        val severity: Severity,
        val code: String,
        val line: Int,
        val detail: String = ""
    )

    data class Report(
        val issues: List<Issue>,
        val interfaceCount: Int,
        val peerCount: Int
    ) {
        val errorCount: Int get() = issues.count { it.severity == Severity.ERROR }
        val warningCount: Int get() = issues.count { it.severity == Severity.WARNING }
        val hasErrors: Boolean get() = errorCount > 0
    }

    private val INTERFACE_KEYS = setOf(
        "privatekey", "address", "dns", "mtu", "listenport", "fwmark",
        "table", "preup", "predown", "postup", "postdown", "saveconfig"
    )

    private val PEER_KEYS = setOf(
        "publickey", "presharedkey", "allowedips", "endpoint", "persistentkeepalive"
    )

    fun check(configText: String): Report {
        if (configText.isBlank()) {
            return Report(listOf(Issue(Severity.ERROR, "empty_config", 1)), 0, 0)
        }

        val issues = mutableListOf<Issue>()
        var interfaceCount = 0
        var peerCount = 0
        var section: String? = null
        var sectionLine = 1
        val keys = HashMap<String, Int>()
        var sawPrivateKey = false
        var sawPublicKey = false

        fun closeSection() {
            when (section) {
                "interface" -> {
                    if (!sawPrivateKey) {
                        issues += Issue(Severity.ERROR, "missing_private_key", sectionLine)
                    }
                    if ("address" !in keys) {
                        issues += Issue(Severity.ERROR, "missing_address", sectionLine)
                    }
                }
                "peer" -> {
                    if (!sawPublicKey) {
                        issues += Issue(Severity.ERROR, "missing_public_key", sectionLine)
                    }
                    if ("allowedips" !in keys) {
                        issues += Issue(Severity.WARNING, "missing_allowed_ips", sectionLine)
                    }
                    if ("endpoint" !in keys) {
                        issues += Issue(Severity.WARNING, "missing_endpoint", sectionLine)
                    }
                }
            }
        }

        configText.lines().forEachIndexed { index, raw ->
            val lineNo = index + 1
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                return@forEachIndexed
            }

            if (line.startsWith("[")) {
                closeSection()
                val name = line.removePrefix("[").substringBefore(']').trim()
                    .lowercase(Locale.US)
                when (name) {
                    "interface" -> {
                        interfaceCount++
                        if (interfaceCount > 1) {
                            issues += Issue(Severity.ERROR, "multiple_interface", lineNo)
                        }
                    }
                    "peer" -> peerCount++
                    else -> issues += Issue(Severity.ERROR, "unknown_section", lineNo, line)
                }
                section = if (name == "interface" || name == "peer") name else null
                sectionLine = lineNo
                keys.clear()
                sawPrivateKey = false
                sawPublicKey = false
                return@forEachIndexed
            }

            val currentSection = section
            if (currentSection == null) {
                issues += Issue(Severity.WARNING, "stray_line", lineNo, line.take(60))
                return@forEachIndexed
            }

            val eq = line.indexOf('=')
            if (eq <= 0) {
                issues += Issue(Severity.WARNING, "unparsed_line", lineNo, line.take(60))
                return@forEachIndexed
            }
            val keyRaw = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            val key = keyRaw.lowercase(Locale.US)
            if (key.isEmpty()) {
                issues += Issue(Severity.WARNING, "unparsed_line", lineNo, line.take(60))
                return@forEachIndexed
            }
            if (keys.containsKey(key)) {
                issues += Issue(Severity.WARNING, "duplicate_key", lineNo, keyRaw)
            }
            keys[key] = lineNo

            when (key) {
                "privatekey" -> {
                    sawPrivateKey = true
                    if (!KeyUtils.isValidBase64Key(value)) {
                        issues += Issue(Severity.ERROR, "invalid_key", lineNo, keyRaw)
                    }
                }
                "publickey" -> {
                    sawPublicKey = true
                    if (!KeyUtils.isValidBase64Key(value)) {
                        issues += Issue(Severity.ERROR, "invalid_key", lineNo, keyRaw)
                    }
                }
                "presharedkey" -> {
                    if (!KeyUtils.isValidBase64Key(value)) {
                        issues += Issue(Severity.ERROR, "invalid_key", lineNo, keyRaw)
                    }
                }
                "address" -> value.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { token ->
                        if (!isIpOrCidr(token)) {
                            issues += Issue(Severity.ERROR, "invalid_cidr", lineNo, token)
                        }
                    }
                "allowedips" -> value.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { token ->
                        if (!isIpOrCidr(token)) {
                            issues += Issue(Severity.ERROR, "invalid_cidr", lineNo, token)
                        } else if (token == "0.0.0.0/0" || token == "::/0") {
                            issues += Issue(Severity.INFO, "full_tunnel", lineNo, token)
                        }
                    }
                "dns" -> value.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { token ->
                        if (!isIpv4(token) && !isIpv6(token) && !isHostname(token)) {
                            issues += Issue(Severity.WARNING, "invalid_dns", lineNo, token)
                        }
                    }
                "mtu" -> {
                    val mtu = value.toIntOrNull()
                    when {
                        mtu == null || mtu !in 576..65535 ->
                            issues += Issue(Severity.ERROR, "invalid_mtu", lineNo, value)
                        mtu < 1280 ->
                            issues += Issue(Severity.WARNING, "low_mtu", lineNo, value)
                    }
                }
                "listenport" -> {
                    val port = value.toIntOrNull()
                    if (port == null || port !in 0..65535) {
                        issues += Issue(Severity.ERROR, "invalid_listen_port", lineNo, value)
                    }
                }
                "persistentkeepalive" -> {
                    val seconds = value.toIntOrNull()
                    if (seconds == null || seconds !in 0..65535) {
                        issues += Issue(Severity.ERROR, "invalid_keepalive", lineNo, value)
                    }
                }
                "endpoint" -> {
                    if (!isEndpoint(value)) {
                        issues += Issue(Severity.ERROR, "invalid_endpoint", lineNo, value)
                    }
                }
                else -> {
                    val known =
                        if (currentSection == "interface") INTERFACE_KEYS else PEER_KEYS
                    if (key !in known) {
                        issues += Issue(Severity.WARNING, "unknown_key", lineNo, keyRaw)
                    }
                }
            }
        }
        closeSection()

        if (interfaceCount == 0) {
            issues += Issue(Severity.ERROR, "missing_interface", 1)
        }
        if (peerCount == 0) {
            issues += Issue(Severity.WARNING, "no_peer", 1)
        }
        return Report(issues.sortedBy { it.line }, interfaceCount, peerCount)
    }

    // ------------------------------------------------------------------
    // Validators — explicit, human-explainable, DNS-free (never resolves).
    // ------------------------------------------------------------------

    private val IPV4_REGEX = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    /** Strict dotted-quad IPv4 (each octet 0–255). */
    fun isIpv4(value: String): Boolean {
        val match = IPV4_REGEX.matchEntire(value) ?: return false
        return match.groupValues.drop(1).all { it.toIntOrNull() in 0..255 }
    }

    /**
     * Pragmatic IPv6: hex/colon/dot characters only, at most one "::",
     * groups of up to 4 hex digits with an optional trailing IPv4 quad,
     * exactly 8 groups when uncompressed and at most 7 with "::" present.
     */
    fun isIpv6(value: String): Boolean {
        if (value.length < 2) return false
        if (!value.all {
                it.isDigit() || it in 'a'..'f' || it in 'A'..'F' ||
                    it == ':' || it == '.'
            }
        ) return false
        if (!value.contains(':')) return false
        val halves = value.split("::")
        if (halves.size > 2) return false

        fun groupCount(part: String): Int? {
            if (part.isEmpty()) return 0
            val groups = part.split(':')
            groups.forEachIndexed { i, group ->
                if (group.isEmpty()) return null
                if (group.contains('.')) {
                    if (i != groups.lastIndex || !isIpv4(group)) return null
                } else {
                    if (group.length > 4) return null
                    if (group.any { Character.digit(it, 16) < 0 }) return null
                }
            }
            return groups.size
        }

        val head = groupCount(halves[0]) ?: return false
        val tail = if (halves.size == 2) groupCount(halves[1]) ?: return false else 0
        return if (halves.size == 2) head + tail <= 7 else head + tail == 8
    }

    /** IPv4/IPv6 address, optionally with a /prefix (0–32 v4, 0–128 v6). */
    fun isIpOrCidr(token: String): Boolean {
        if (token.isEmpty()) return false
        val slash = token.indexOf('/')
        if (slash == -1) return isIpv4(token) || isIpv6(token)
        val ip = token.substring(0, slash)
        val prefix = token.substring(slash + 1).toIntOrNull() ?: return false
        return when {
            isIpv4(ip) -> prefix in 0..32
            isIpv6(ip) -> prefix in 0..128
            else -> false
        }
    }

    /** ASCII hostname: letters/digits/hyphen labels, no leading/trailing hyphen. */
    fun isHostname(value: String): Boolean {
        if (value.isEmpty() || value.length > 253) return false
        if (value.contains("..")) return false
        return value.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' } &&
                !label.startsWith("-") && !label.endsWith("-")
        }
    }

    /** host:port or [ipv6]:port with port 1–65535. Never resolves the host. */
    fun isEndpoint(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return false
        val host: String
        val portPart: String
        if (v.startsWith("[")) {
            val close = v.indexOf(']')
            if (close == -1) return false
            host = v.substring(1, close)
            if (v.length <= close + 1 || v[close + 1] != ':') return false
            portPart = v.substring(close + 2)
        } else {
            val colon = v.lastIndexOf(':')
            if (colon == -1) return false
            host = v.substring(0, colon)
            portPart = v.substring(colon + 1)
        }
        val port = portPart.toIntOrNull() ?: return false
        if (port !in 1..65535) return false
        return isIpv4(host) || isIpv6(host) || isHostname(host)
    }
}
