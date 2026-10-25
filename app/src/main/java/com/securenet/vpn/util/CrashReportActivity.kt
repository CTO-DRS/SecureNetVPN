package com.securenet.vpn.util

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * v1.6.2 — Crash-report viewer that runs in a SEPARATE PROCESS
 * (android:process=":crash_report"). When the main process dies, this
 * activity is started directly from the uncaught-exception handler; because
 * it lives in its own process it keeps rendering even after the crashing
 * process is killed by the system handler.
 *
 * The UI is built 100% programmatically on the framework Material theme —
 * zero app-theme, AppCompat or XML-layout dependencies — so this screen can
 * never crash for the same reason the app just did.
 */
class CrashReportActivity : android.app.Activity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = readReport()

        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12141A"))
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "⚠ SecureNet VPN — تقرير عطل / Crash report"
            setTextColor(Color.parseColor("#FFB74D"))
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "حدث عطل غير متوقع. أرسل هذا التقرير للمطوّر لتحديد السبب.\n" +
                "An unexpected crash occurred — please copy or share this report."
            setTextColor(Color.parseColor("#B0B6C0"))
            textSize = 13f
            setPadding(0, pad / 2, 0, pad)
        })

        val traceView = TextView(this).apply {
            text = report
            setTextColor(Color.parseColor("#9CDCAA"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply {
            addView(traceView)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(label: String, onTap: () -> Unit): Button =
            Button(this).apply {
                text = label
                setOnClickListener {
                    runCatching { onTap() }
                        .onFailure { Toast.makeText(context, "error", Toast.LENGTH_SHORT).show() }
                }
            }

        buttons.addView(action("نسخ / Copy") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash_report", report))
            Toast.makeText(this, "تم النسخ ✓ Copied", Toast.LENGTH_SHORT).show()
        })
        buttons.addView(action("مشاركة / Share") {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SecureNet VPN crash report")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            startActivity(Intent.createChooser(send, "Share crash report"))
        })
        buttons.addView(action("إغلاق / Close") {
            finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(0)
        })
        root.addView(buttons)

        setContentView(root)
    }

    private fun readReport(): String {
        val file = CrashReporter.reportFile(this)
        return runCatching {
            if (file.exists()) file.readText() else "(no report file found)"
        }.getOrDefault("(unreadable report file)")
    }
}
