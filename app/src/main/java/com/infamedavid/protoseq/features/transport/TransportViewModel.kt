package com.infamedavid.protoseq.features.transport

import androidx.lifecycle.ViewModel
import com.infamedavid.protoseq.core.clock.ClockEngine
import com.infamedavid.protoseq.core.clock.TransportState
import com.infamedavid.protoseq.core.midi.MidiDeviceRepository
import com.infamedavid.protoseq.core.midi.MidiEngine
import com.infamedavid.protoseq.core.midi.NoOpMidiMessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransportViewModel(
    private val clockEngine: ClockEngine = ClockEngine(),
    private val midiEngine: MidiEngine = MidiEngine(
        messageSender = NoOpMidiMessageSender(),
        midiDeviceRepository = MidiDeviceRepository()
    )
) : ViewModel() {
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(
        TransportUiState(
            bpm = clockEngine.getBpm().toFloat(),
            state = clockEngine.getTransportState()
        )
    )
    val uiState: StateFlow<TransportUiState> = _uiState.asStateFlow()

    init {
        vmScope.launch {
            clockEngine.transportState.collect { state ->
                _uiState.value = _uiState.value.copy(state = state)
            }
        }

        vmScope.launch {
            clockEngine.ticks.collect { tick ->
                if (tick % 4L == 0L) {
                    midiEngine.sendClock()
                }
            }
        }
    }

    fun setBpm(bpm: Float) {
        val clampedBpm = bpm.coerceIn(40f, 240f)
        clockEngine.setBpm(clampedBpm.toDouble())
        _uiState.value = _uiState.value.copy(bpm = clampedBpm)
    }

    fun play() {
        when (clockEngine.getTransportState()) {
            TransportState.Stopped -> {
                clockEngine.playFromStart()
                midiEngine.sendStart()
            }

            TransportState.Paused -> {
                clockEngine.resume()
                midiEngine.sendContinue()
            }

            TransportState.Playing -> Unit
        }
    }

    fun stop() {
        when (clockEngine.getTransportState()) {
            TransportState.Playing,
            TransportState.Paused -> {
                clockEngine.stop()
                midiEngine.sendStop()
            }

            TransportState.Stopped -> Unit
        }
    }

    fun pause() {
        if (clockEngine.getTransportState() == TransportState.Playing) {
            clockEngine.pause()
        }
    }

    override fun onCleared() {
        super.onCleared()
        vmScope.cancel()
    }
}
