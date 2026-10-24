package com.securenet.vpn.ui.stats

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.securenet.vpn.R
import com.securenet.vpn.data.local.CustomServerStore
import com.securenet.vpn.data.local.HistoryParser
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.stats.MonthlyStatsEngine
import com.securenet.vpn.stats.ServerStatsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.6 — Per-server usage statistics: one row per server the device ever
 * connected to, with session count, connected time, failure count, last
 * use and a share-of-time bar. Data is re-read from the encrypted session
 * log each time the sheet opens; server names are resolved against the
 * repository list + imported profiles, falling back to the raw id.
 */
class ServerStatsDialogFragment : DialogFragment() {

    private var _binding: com.securenet.vpn.databinding.DialogServerStatsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = com.securenet.vpn.databinding.DialogServerStatsBinding.inflate(layoutInflater)
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.server_stats_title)
            .setView(binding.root)
            .setPositiveButton(R.string.cancel, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        load()
    }

    private fun load() {
        val container = (requireActivity().application as AppProvider).provideContainer()
        viewLifecycleOwner.lifecycleScope.launch {
            val usages = withContext(Dispatchers.IO) {
                val timeline = HistoryParser().parse(container.connectionLogger.readAllLines())
                ServerStatsEngine.compute(timeline.sessions)
            }
            val names = withContext(Dispatchers.IO) {
                buildMap {
                    runCatching {
                        container.serverRepository.getServers(forceRefresh = false)
                    }.getOrNull()?.forEach { put(it.id, it.name) }
                    runCatching {
                        CustomServerStore.load(container.securePrefs.customServersJson)
                    }.getOrNull()?.forEach { put(it.server.id, it.server.name) }
                }
            }
            render(usages, names)
        }
    }

    private fun render(usages: List<ServerStatsEngine.ServerUsage>, names: Map<String, String>) {
        binding.progressServerStats.visibility = View.GONE
        if (usages.isEmpty()) {
            binding.tvServerStatsEmpty.visibility = View.VISIBLE
            binding.listServerStats.visibility = View.GONE
            return
        }
        binding.tvServerStatsEmpty.visibility = View.GONE
        binding.listServerStats.visibility = View.VISIBLE
        binding.listServerStats.removeAllViews()

        val context = requireContext()
        val density = resources.displayMetrics.density
        usages.forEachIndexed { index, usage ->
            if (index > 0) {
                binding.listServerStats.addView(
                    View(context).apply {
                        setBackgroundColor(
                            context.getColor(R.color.glass_fill_strong)
                        )
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()
                        ).apply { setMargins(0, dp(8, density), 0, dp(8, density)) }
                    }
                )
            }

            val name = TextView(context).apply {
                text = ServerStatsEngine.displayName(usage.serverId, names)
                setTextColor(context.getColor(R.color.on_glass_primary))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val meta = TextView(context).apply {
                text = getString(
                    R.string.server_stats_row_meta,
                    usage.sessions.toString(),
                    MonthlyStatsEngine.formatDuration(usage.connectedSeconds),
                    usage.failures.toString()
                )
                setTextColor(context.getColor(R.color.on_glass_secondary))
                textSize = 12f
            }

            val lastUsed = TextView(context).apply {
                text = getString(
                    R.string.server_stats_last_used,
                    if (usage.lastUsedMillis > 0)
                        LAST_USED_FORMAT.format(Date(usage.lastUsedMillis)) else "—"
                )
                setTextColor(context.getColor(R.color.on_glass_secondary))
                textSize = 12f
                gravity = Gravity.END
            }

            val share = ServerStatsEngine.shareSeconds(usage, usages)
            val barFill = View(context).apply {
                setBackgroundColor(context.getColor(R.color.chart_rx))
                layoutParams = LinearLayout.LayoutParams(0, dp(6, density), maxOf(share.toFloat(), 0.5f))
            }
            val barTrack = View(context).apply {
                setBackgroundColor(context.getColor(R.color.glass_fill_strong))
                layoutParams = LinearLayout.LayoutParams(0, dp(6, density), maxOf(100f - share, 0.5f))
            }
            val bar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6, density) }
                addView(barFill)
                addView(barTrack)
            }

            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = name.text
                    setTextColor(name.textColors)
                    textSize = name.textSize / resources.displayMetrics.density
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(lastUsed)
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                addView(header)
                addView(meta)
                addView(bar)
            }

            binding.listServerStats.addView(row)
        }
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        val LAST_USED_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
}
