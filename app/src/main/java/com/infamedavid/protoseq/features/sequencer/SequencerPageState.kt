package com.infamedavid.protoseq.features.sequencer

import com.infamedavid.protoseq.features.stochastic.StochasticSequencerUiState

data class SequencerPageState(
    val pageIndex: Int,
    val enabled: Boolean = false,
    val selectedSequencerType: SequencerType = SequencerType.EMPTY,
    val turingState: StochasticSequencerUiState = StochasticSequencerUiState(),
) {
    fun isEmpty(): Boolean = selectedSequencerType == SequencerType.EMPTY

    fun isTuringMachine(): Boolean = selectedSequencerType == SequencerType.TURING_MACHINE

    fun withSequencerType(type: SequencerType): SequencerPageState =
        copy(selectedSequencerType = type)
}
