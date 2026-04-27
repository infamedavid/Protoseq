package com.infamedavid.protoseq.features.sequencer

import com.infamedavid.protoseq.features.ginaarp.GinaArpSequencerUiState
import com.infamedavid.protoseq.features.grid616.Grid616SequencerUiState
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerUiState
import com.infamedavid.protoseq.features.stp116.Stp116SequencerUiState

data class SequencerPageState(
    val pageIndex: Int,
    val enabled: Boolean = false,
    val selectedSequencerType: SequencerType = SequencerType.EMPTY,
    val turingState: StochasticSequencerUiState = StochasticSequencerUiState(),
    val grid616State: Grid616SequencerUiState = Grid616SequencerUiState(),
    val ginaArpState: GinaArpSequencerUiState = GinaArpSequencerUiState(),
    val stp116State: Stp116SequencerUiState = Stp116SequencerUiState(),
) {
    fun isEmpty(): Boolean = selectedSequencerType == SequencerType.EMPTY

    fun isTuringMachine(): Boolean = selectedSequencerType == SequencerType.TURING_MACHINE

    fun withSequencerType(type: SequencerType): SequencerPageState =
        copy(selectedSequencerType = type)
}
