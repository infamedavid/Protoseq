package com.infamedavid.protoseq.features.transport

import com.infamedavid.protoseq.core.clock.TransportState
import com.infamedavid.protoseq.core.midi.MidiOutputTarget

data class TransportUiState(
    val bpm: Float = 120f,
    val state: TransportState = TransportState.Stopped,
    val midiOutputTargets: List<MidiOutputTarget> = emptyList(),
    val selectedMidiOutputId: String? = null
)
