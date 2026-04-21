package com.infamedavid.protoseq.features.stochastic

import com.infamedavid.protoseq.core.music.QuantizationMode
import com.infamedavid.protoseq.core.repeater.RptrStartMode

data class StochasticSequencerConfig(
    val sequenceLength: Int,
    val lockPosition: Float,
    val midiChannel: Int,
    val outputMode: MidiOutputMode,
    val ccNumber: Int,
    val baseNote: Int,
    val quantizationMode: QuantizationMode,
    val slewAmount: Float,
    val pitchRangeSemitones: Int,
    val pitchOffset: Int,
    val gateLength: Float,
    val randomGateLength: Float,
    val bernoulliProbability: Float,
    val rptrBaseUnits: Int,
    val rptrStartMode: RptrStartMode,
) {
    fun sanitized(registerSize: Int = 64): StochasticSequencerConfig {
        return copy(
            sequenceLength = sequenceLength.coerceIn(2, registerSize),
            lockPosition = lockPosition.coerceIn(-1f, 1f),
            midiChannel = midiChannel.coerceIn(1, 16),
            ccNumber = ccNumber.coerceIn(0, 127),
            baseNote = baseNote.coerceIn(0, 127),
            slewAmount = slewAmount.coerceIn(0f, 1f),
            pitchRangeSemitones = pitchRangeSemitones.coerceIn(1, 64),
            pitchOffset = pitchOffset.coerceIn(-24, 24),
            gateLength = gateLength.coerceIn(0f, 1f),
            randomGateLength = randomGateLength.coerceIn(0f, 1f),
            bernoulliProbability = bernoulliProbability.coerceIn(0f, 1f),
            rptrBaseUnits = rptrBaseUnits.coerceAtLeast(1)
        )
    }
}

fun StochasticSequencerUiState.toConfig(): StochasticSequencerConfig {
    return StochasticSequencerConfig(
        sequenceLength = sequenceLength,
        lockPosition = lockPosition,
        midiChannel = midiChannel,
        outputMode = outputMode,
        ccNumber = ccNumber,
        baseNote = baseNote,
        quantizationMode = quantizationMode,
        slewAmount = slewAmount,
        pitchRangeSemitones = pitchRangeSemitones,
        pitchOffset = pitchOffset,
        gateLength = gateLength,
        randomGateLength = randomGateLength,
        bernoulliProbability = bernoulliProbability,
        rptrBaseUnits = rptrBaseUnits,
        rptrStartMode = rptrStartMode
    )
}
