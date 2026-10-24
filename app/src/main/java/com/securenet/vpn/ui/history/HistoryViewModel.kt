package com.securenet.vpn.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securenet.vpn.data.local.ConnectionLogger
import com.securenet.vpn.data.local.HistoryParser
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.stats.MonthlyStatsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * v1.2 — Connection history screen state: sessions, daily usage and the
 * raw event count, all derived on-device from the encrypted session log.
 *
 * v1.5 — adds the monthly dashboard: [monthOffset] (0 = current month,
 * -1 = previous, …) drives a pure [MonthlyStatsEngine] recompute from the
 * already-parsed timeline. Nothing ever leaves the device.
 */
class HistoryViewModel(
    private val logger: ConnectionLogger
) : ViewModel() {

    data class Ui(
        val sessions: List<HistoryParser.Session> = emptyList(),
        val usage: List<HistoryParser.UsageDay> = emptyList(),
        val totalEvents: Int = 0,
        val loading: Boolean = true,
        /** v1.5: monthly dashboard model for the selected month. */
        val monthStats: MonthlyStatsEngine.MonthStats? = null,
        /** 0 = current month, negative = back in time. */
        val monthOffset: Int = 0
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    /** Latest parsed timeline, cached for month switches without re-reading. */
    private var cachedTimeline: HistoryParser.Timeline? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val timeline = withContext(Dispatchers.IO) {
                HistoryParser().parse(logger.readAllLines())
            }
            cachedTimeline = timeline
            _ui.value = _ui.value.copy(
                sessions = timeline.sessions,
                usage = timeline.usage,
                totalEvents = timeline.totalEvents,
                loading = false
            )
            recomputeMonth()
        }
    }

    fun prevMonth() {
        _ui.value = _ui.value.copy(monthOffset = _ui.value.monthOffset - 1)
        recomputeMonth()
    }

    fun nextMonth() {
        val current = _ui.value.monthOffset
        if (current >= 0) return // no peeking into the future
        _ui.value = _ui.value.copy(monthOffset = current + 1)
        recomputeMonth()
    }

    private fun recomputeMonth() {
        val timeline = cachedTimeline ?: return
        val state = _ui.value
        val nowUtc = LocalDate.now(ZoneOffset.UTC)
        val (year, month) = MonthlyStatsEngine.shift(
            nowUtc.year, nowUtc.monthValue, state.monthOffset
        )
        val stats = MonthlyStatsEngine.compute(
            usage = timeline.usage,
            sessions = timeline.sessions,
            year = year,
            month = month,
            nowUtc = nowUtc
        )
        _ui.value = state.copy(monthStats = stats)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(container.connectionLogger) as T
    }
}
