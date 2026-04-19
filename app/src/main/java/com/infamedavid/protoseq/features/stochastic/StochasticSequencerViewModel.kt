package com.infamedavid.protoseq.features.stochastic

import androidx.lifecycle.ViewModel
import com.infamedavid.protoseq.core.music.QuantizationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StochasticSequencerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StochasticSequencerUiState())
    val uiState: StateFlow<StochasticSequencerUiState> = _uiState.asStateFlow()

    fun setLockPosition(value: Float) {
        _uiState.value = _uiState.value.copy(lockPosition = value.coerceIn(-1f, 1f))
    }

    fun setSequenceLength(length: Int) {
        _uiState.value = _uiState.value.copy(sequenceLength = length.coerceIn(2, 64))
    }

    fun setMidiChannel(channel: Int) {
        _uiState.value = _uiState.value.copy(midiChannel = channel.coerceIn(1, 16))
    }

    fun incrementMidiChannel() {
        val next = if (_uiState.value.midiChannel >= 16) 1 else _uiState.value.midiChannel + 1
        setMidiChannel(next)
    }

    fun decrementMidiChannel() {
        val next = if (_uiState.value.midiChannel <= 1) 16 else _uiState.value.midiChannel - 1
        setMidiChannel(next)
    }

    fun setOutputMode(mode: MidiOutputMode) {
        _uiState.value = _uiState.value.copy(outputMode = mode)
    }

    fun setCcNumber(value: Int) {
        _uiState.value = _uiState.value.copy(ccNumber = value.coerceIn(0, 127))
    }

    fun setBaseNote(note: Int) {
        _uiState.value = _uiState.value.copy(baseNote = note.coerceIn(0, 127))
    }

    fun incrementBaseNote() {
        setBaseNote(_uiState.value.baseNote + 1)
    }

    fun decrementBaseNote() {
        setBaseNote(_uiState.value.baseNote - 1)
    }

    fun setQuantizationMode(mode: QuantizationMode) {
        _uiState.value = _uiState.value.copy(quantizationMode = mode)
    }

    fun nextQuantizationMode() {
        val values = QuantizationMode.entries
        val currentIndex = values.indexOf(_uiState.value.quantizationMode)
        val nextIndex = (currentIndex + 1) % values.size
        setQuantizationMode(values[nextIndex])
    }

    fun previousQuantizationMode() {
        val values = QuantizationMode.entries
        val currentIndex = values.indexOf(_uiState.value.quantizationMode)
        val nextIndex = if (currentIndex <= 0) values.lastIndex else currentIndex - 1
        setQuantizationMode(values[nextIndex])
    }

    fun setSlewAmount(amount: Float) {
        _uiState.value = _uiState.value.copy(slewAmount = amount.coerceIn(0f, 1f))
    }

    fun setPitchRangeOctaves(octaves: Int) {
        _uiState.value = _uiState.value.copy(pitchRangeOctaves = octaves.coerceIn(1, 5))
    }

    fun setPitchOffset(offset: Int) {
        _uiState.value = _uiState.value.copy(pitchOffset = offset.coerceIn(-24, 24))
    }

    fun setGateLength(length: Float) {
        _uiState.value = _uiState.value.copy(gateLength = length.coerceIn(0f, 1f))
    }

    fun setRandomGateLength(length: Float) {
        _uiState.value = _uiState.value.copy(randomGateLength = length.coerceIn(0f, 1f))
    }

    fun setBernoulliProbability(probability: Float) {
        _uiState.value = _uiState.value.copy(bernoulliProbability = probability.coerceIn(0f, 1f))
    }
}
