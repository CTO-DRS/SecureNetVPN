package com.securenet.vpn.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.securenet.vpn.R
import com.securenet.vpn.databinding.FragmentHistoryBinding
import com.securenet.vpn.stats.MonthlyStatsEngine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * v1.2 — Connection history: sessions and per-day usage, read from the
 * encrypted session log. Nothing leaves the device.
 *
 * v1.5 — top card hosts the monthly stats dashboard: month switcher,
 * summary grid, stacked daily bar chart and a live caption for the tapped
 * / peak day.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels {
        (requireActivity().application as com.securenet.vpn.di.AppProvider)
            .provideContainer()
            .let { HistoryViewModel.Factory(it) }
    }

    private val adapter = HistoryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter

        binding.btnMonthPrev.setOnClickListener { viewModel.prevMonth() }
        binding.btnMonthNext.setOnClickListener { viewModel.nextMonth() }

        binding.chartDaily.setOnBarSelectedListener(object :
            com.securenet.vpn.ui.stats.StatsBarChartView.OnBarSelectedListener {
            override fun onBarSelected(
                dayIndex: Int,
                bar: com.securenet.vpn.ui.stats.StatsBarChartView.Bar
            ) {
                val stats = viewModel.ui.value.monthStats ?: return
                binding.tvChartCaption.text = dayCaption(stats, dayIndex)
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ui.collect { ui ->
                    adapter.submit(ui.sessions, ui.usage)
                    binding.tvHistoryEmpty.visibility =
                        if (ui.sessions.isEmpty() && ui.usage.isEmpty()) View.VISIBLE
                        else View.GONE
                    binding.tvHistoryMeta.text =
                        getString(R.string.history_meta, ui.totalEvents)
                    renderStats(ui)
                }
            }
        }
    }

    private fun renderStats(ui: HistoryViewModel.Ui) {
        val stats = ui.monthStats ?: return
        val context = requireContext()

        // Month switcher title + next-button gating.
        binding.tvMonthTitle.text = monthTitle(stats.year, stats.month)
        binding.btnMonthNext.isEnabled = ui.monthOffset < 0
        binding.btnMonthNext.alpha = if (ui.monthOffset < 0) 1f else 0.35f

        // Summary grid.
        binding.tvStatTotal.text = MonthlyStatsEngine.formatBytes(stats.totalBytes)
        binding.tvStatDownload.text = MonthlyStatsEngine.formatBytes(stats.totalRxBytes)
        binding.tvStatUpload.text = MonthlyStatsEngine.formatBytes(stats.totalTxBytes)
        binding.tvStatSessions.text = context.getString(
            R.string.stats_sessions_value,
            stats.sessions.toString(),
            MonthlyStatsEngine.formatDuration(stats.connectedSeconds)
        )

        // Chart.
        val bars = stats.days.map { day ->
            com.securenet.vpn.ui.stats.StatsBarChartView.Bar(
                rxMb = day.rxBytes / MB_F,
                txMb = day.txBytes / MB_F
            )
        }
        binding.chartDaily.setData(bars, stats.todayIndex)

        // Empty month → swap chart out for the empty hint, drop caption.
        val hasData = stats.totalBytes > 0
        binding.chartDaily.visibility = if (hasData) View.VISIBLE else View.GONE
        binding.tvStatsEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
        binding.tvChartCaption.text = if (hasData) {
            context.getString(
                R.string.stats_caption_summary,
                MonthlyStatsEngine.formatBytes(stats.avgDailyBytes),
                stats.activeDays.toString()
            )
        } else {
            ""
        }
    }

    private fun dayCaption(
        stats: MonthlyStatsEngine.MonthStats,
        dayIndex: Int
    ): String {
        val day = stats.days.getOrNull(dayIndex) ?: return ""
        val date = LocalDate.ofEpochDay(day.dayEpoch)
        return getString(
            R.string.stats_caption_day,
            date.dayOfMonth.toString(),
            monthTitle(stats.year, stats.month),
            MonthlyStatsEngine.formatBytes(day.rxBytes),
            MonthlyStatsEngine.formatBytes(day.txBytes)
        )
    }

    private fun monthTitle(year: Int, month: Int): String {
        val locale = resources.configuration.locales[0]
        val name = Month.of(month).getDisplayName(TextStyle.FULL, locale)
        return "$name $year"
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MB_F = 1024f * 1024f
    }
}
