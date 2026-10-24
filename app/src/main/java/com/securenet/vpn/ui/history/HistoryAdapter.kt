package com.securenet.vpn.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.securenet.vpn.R
import com.securenet.vpn.data.local.HistoryParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.2 — Renders the history timeline: usage day headers and session rows.
 */
class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.RowHolder>() {

    sealed class Row {
        data class Usage(val day: HistoryParser.UsageDay) : Row()
        data class Session(val session: HistoryParser.Session) : Row()
    }

    private val rows = mutableListOf<Row>()

    fun submit(sessions: List<HistoryParser.Session>, usage: List<HistoryParser.UsageDay>) {
        rows.clear()
        usage.forEach { rows += Row.Usage(it) }
        sessions.forEach { rows += Row.Session(it) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_entry, parent, false)
        return RowHolder(view)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.bind(rows[position])
    }

    class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.ivHistoryIcon)
        private val title: TextView = itemView.findViewById(R.id.tvHistoryTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.tvHistorySubtitle)
        private val meta: TextView = itemView.findViewById(R.id.tvHistoryMeta)
        private val timeFormat =
            SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault())

        fun bind(row: Row) {
            val ctx = itemView.context
            when (row) {
                is Row.Usage -> {
                    icon.setImageResource(R.drawable.ic_speed)
                    icon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_teal))
                    title.text = ctx.getString(R.string.history_usage_title)
                    subtitle.text = ctx.getString(
                        R.string.history_usage_bytes,
                        formatBytes(row.day.rxBytes),
                        formatBytes(row.day.txBytes)
                    )
                    meta.text = ctx.getString(
                        R.string.history_usage_time,
                        formatDuration(row.day.connectedSeconds)
                    )
                }
                is Row.Session -> {
                    val open = row.session.isOpen
                    val failed = row.session.endEvent == "failed"
                    icon.setImageResource(
                        when {
                            open -> R.drawable.ic_lock
                            failed -> R.drawable.ic_warning
                            else -> R.drawable.ic_check
                        }
                    )
                    icon.setColorFilter(
                        ContextCompat.getColor(
                            ctx,
                            when {
                                open -> R.color.status_connected
                                failed -> R.color.status_failed
                                else -> R.color.accent_teal
                            }
                        )
                    )
                    title.text = if (open) {
                        ctx.getString(R.string.history_session_open)
                    } else {
                        ctx.getString(
                            if (failed) R.string.history_session_failed
                            else R.string.history_session_ok
                        )
                    }
                    subtitle.text = row.session.serverId
                        ?: ctx.getString(R.string.history_unknown_server)
                    meta.text = timeFormat.format(Date(row.session.startedAtMillis)) +
                        " · " + formatDuration(row.session.durationMillis / 1000)
                }
            }
        }

        private fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
            return String.format(Locale.US, "%.2f GB", mb / 1024.0)
        }

        private fun formatDuration(seconds: Long): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
            else String.format(Locale.US, "%02d:%02d", m, s)
        }
    }
}
