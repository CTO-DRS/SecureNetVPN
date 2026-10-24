package com.securenet.vpn.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.data.model.ScanResult
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.domain.usecase.ScanDeviceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScannerViewModel(
    private val scanDevice: ScanDeviceUseCase,
    private val securePrefs: SecurePrefs
) : ViewModel() {

    sealed class ScanUiState {
        object Idle : ScanUiState()
        object Scanning : ScanUiState()
        data class Done(val result: ScanResult) : ScanUiState()
        data class Failed(val reason: String) : ScanUiState()
    }

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun startScan() {
        if (_uiState.value is ScanUiState.Scanning) return
        _uiState.value = ScanUiState.Scanning
        viewModelScope.launch {
            try {
                val result = scanDevice()
                securePrefs.lastScanAt = result.completedAtMillis
                securePrefs.lastScanSummary = "${result.threats.size} findings"
                _uiState.value = ScanUiState.Done(result)
            } catch (t: Throwable) {
                _uiState.value = ScanUiState.Failed(t.message ?: "unknown")
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScannerViewModel(
            container.scanDeviceUseCase,
            container.securePrefs
        ) as T
    }
}
