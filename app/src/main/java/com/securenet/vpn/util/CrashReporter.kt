package com.securenet.vpn.util

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.6.1 — Global crash capture: writes the stack trace of any uncaught
 * exception to filesDir/crash_reports/last_crash.txt before delegating to
 * the system handler, so field failures leave a diagnosis trail instead of
 * dying silently.
 *
 * v1.6.2 — On crash, additionally launches [CrashReportActivity] which runs
 * in a SEPARATE PROCESS (:crash_report). The start request is committed to
 * system_server before the previous (system) handler kills the crashing
 * process, so the on-screen report survives the process death and the user
 * can copy/share the actual stack trace. Everything is defensive: crash
 * reporting must never itself crash or alter the failure path.
 */
object CrashReporter {

    private const val DIR_NAME = "crash_reports"
    private const val FILE_NAME = "last_crash.txt"
    private const val SEEN_SUFFIX = ".seen"
    private const val MAX_BYTES = 256 * 1024

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val written = runCatching { write(appContext, thread, throwable) }.isSuccess
            // Launch the separate-process report screen BEFORE delegating to
            // the system handler (which kills the process).
            if (written) {
                runCatching {
                    appContext.startActivity(
                        Intent(appContext, CrashReportActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        )
                    )
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Location of the latest crash report (may not exist yet). */
    fun reportFile(context: Context): File =
        File(File(context.applicationContext.filesDir, DIR_NAME), FILE_NAME)

    /** Latest captured crash report, or null when there is none pending. */
    fun lastReport(context: Context): String? =
        try {
            reportFile(context).takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
        }

    /**
     * Marks the pending report as shown (renames instead of deleting so the
     * evidence survives) — called after the in-app dialog displayed it.
     */
    fun markReportHandled(context: Context) {
        runCatching {
            val file = reportFile(context)
            if (file.exists()) file.renameTo(File(file.parentFile, FILE_NAME + SEEN_SUFFIX))
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()

        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
            .toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            .format(Date())

        val body = buildString {
            append("time=").append(timestamp).append('\n')
            append("thread=").append(thread.name).append('\n')
            append("version=")
            runCatching {
                append(context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName)
            }.onFailure { append("unknown") }
            append("\n\n")
            append(stack)
        }

        // Bound the file: keep the tail (the deepest frames matter most).
        val bytes = body.toByteArray(Charsets.UTF_8)
        val payload = if (bytes.size > MAX_BYTES) {
            bytes.copyOfRange(bytes.size - MAX_BYTES, bytes.size).toString(Charsets.UTF_8)
        } else {
            body
        }
        File(dir, FILE_NAME).writeText(payload)
    }
}
