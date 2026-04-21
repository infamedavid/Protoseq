package com.infamedavid.protoseq.features.transport

import com.infamedavid.protoseq.core.clock.TransportState
import com.infamedavid.protoseq.core.midi.MidiOutputTarget
import com.infamedavid.protoseq.core.repeater.RptrDivision

sealed interface RptrUiRuntimeState {
    data object Idle : RptrUiRuntimeState
    data object Wait : RptrUiRuntimeState
    data object Record : RptrUiRuntimeState
    data object Loop : RptrUiRuntimeState
}

data class TransportUiState(
    val bpm: Float = 120f,
    val state: TransportState = TransportState.Stopped,
    val midiOutputTargets: List<MidiOutputTarget> = emptyList(),
    val selectedMidiOutputId: String? = null,
    val rptrState: RptrUiRuntimeState = RptrUiRuntimeState.Idle,
    val activeRptrDivision: RptrDivision? = null
)
