package com.securenet.vpn.util

/**
 * v1.8.19 — 📤 share-text builder for the WireGuard config checker's
 * report (مشاركة تقرير الفحص). After a check runs, the sheet offers a
 * share button so the user can send the diagnosis to a friend or a more
 * experienced admin — the exact lines the sheet already showed, nothing
 * more: a neutral header naming the app version, the verdict line, the
 * parsed shape, the issue bullets and an honesty footer (the check ran
 * locally; nothing was uploaded — sharing the TEXT is the user's own
 * choice, the config itself is never attached).
 *
 * Pure function (strings in, string out) so the shape is JVM-pinned by
 * [WireCheckShareTest].
 */
object WireCheckShare {

    /**
     * Builds the shareable report text from the already-resolved UI lines:
     * [resultLine] is the verdict ("3 errors, 2 warnings" / "looks good"),
     * [shapeLine] the "N × [Interface] · N × [Peer]" summary and [issues]
     * the per-issue lines with their severity icons.
     */
    fun shareText(
        appVersion: String,
        resultLine: String,
        shapeLine: String,
        issues: List<String>
    ): String = buildString {
        append("SecureNet VPN v").append(appVersion)
        append(" — WireGuard config check\n")
        append(resultLine).append('\n')
        append(shapeLine).append('\n')
        if (issues.isNotEmpty()) {
            append('\n')
            issues.forEach { append("• ").append(it).append('\n') }
        }
        append("\nChecked locally on the device — nothing was uploaded.")
    }
}
