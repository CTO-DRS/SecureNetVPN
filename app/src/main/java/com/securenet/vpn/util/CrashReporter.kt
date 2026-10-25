package com.securenet.vpn.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.6.1 — Global crash capture. Installed as the default uncaught-exception
 * handler before anything else runs, so any field failure (unknown OEM
 * quirks, keystore issues, resource problems) leaves a stack trace in
 * filesDir/crash_reports/last_crash.txt for diagnosis instead of dying
 * silently. The previous system handler is always delegated to afterwards,
 * so system crash dialogs behave exactly as before.
 *
 * Writes are fully defensive: crash reporting must never itself crash or
 * alter the failure path.
 */
object CrashReporter {

    private const val DIR_NAME = "crash_reports"
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_BYTES = 256 * 1024

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Location of the latest crash report (may not exist yet). */
    fun reportFile(context: Context): File =
        File(File(context.applicationContext.filesDir, DIR_NAME), FILE_NAME)

    /** Latest captured crash report, or null when the app has not crashed. */
    fun lastReport(context: Context): String? =
        try {
            reportFile(context).takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
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
        val payload = if (body.toByteArray().size > MAX_BYTES) {
            body.toByteArray().copyOfRange(body.toByteArray().size - MAX_BYTES, body.toByteArray().size)
                .toString(Charsets.UTF_8)
        } else {
            body
        }
        File(dir, FILE_NAME).writeText(payload)
    }
}
