package com.infamedavid.protoseq.features.stochastic

import com.infamedavid.protoseq.core.music.QuantizationMode

data class StochasticSequencerUiState(
    val sequenceLength: Int = 16,
    val lockAmount: Float = 0.35f,
    val midiChannel: Int = 5,
    val baseNote: Int = 49, // C#3
    val quantizationMode: QuantizationMode = QuantizationMode.PMIN,
    val slewAmount: Float = 0.10f,
    val pitchRangeOctaves: Int = 2,
    val pitchOffset: Int = 0,
    val gateLength: Float = 0.45f,
    val randomGateLength: Float = 0.20f,
    val bernoulliProbability: Float = 0.00f
)