package com.infamedavid.protoseq.features.transport

import androidx.lifecycle.ViewModel
import com.infamedavid.protoseq.core.clock.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TransportViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TransportUiState())
    val uiState: StateFlow<TransportUiState> = _uiState.asStateFlow()

    fun setBpm(bpm: Float) {
        _uiState.value = _uiState.value.copy(bpm = bpm.coerceIn(40f, 240f))
    }

    fun play() {
        _uiState.value = _uiState.value.copy(state = TransportState.Playing)
    }

    fun stop() {
        _uiState.value = _uiState.value.copy(state = TransportState.Stopped)
    }

    fun pause() {
        _uiState.value = _uiState.value.copy(state = TransportState.Paused)
    }
}
