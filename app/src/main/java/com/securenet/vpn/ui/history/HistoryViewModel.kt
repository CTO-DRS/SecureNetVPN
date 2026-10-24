package com.securenet.vpn.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securenet.vpn.data.local.ConnectionLogger
import com.securenet.vpn.data.local.HistoryParser
import com.securenet.vpn.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v1.2 — Connection history screen state: sessions, daily usage and the
 * raw event count, all derived on-device from the encrypted session log.
 */
class HistoryViewModel(
    private val logger: ConnectionLogger
) : ViewModel() {

    data class Ui(
        val sessions: List<HistoryParser.Session> = emptyList(),
        val usage: List<HistoryParser.UsageDay> = emptyList(),
        val totalEvents: Int = 0,
        val loading: Boolean = true
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val timeline = withContext(Dispatchers.IO) {
                HistoryParser().parse(logger.readAllLines())
            }
            _ui.value = Ui(
                sessions = timeline.sessions,
                usage = timeline.usage,
                totalEvents = timeline.totalEvents,
                loading = false
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(container.connectionLogger) as T
    }
}
