package com.infamedavid.protoseq.features.stp116

data class Stp116StepConfig(
    val enabled: Boolean,
    val pitchClass: Int,
    val octave: Int,
    val midiNote: Int,
    val fineTuneCents: Int,
    val gateDelayTicks: Int,
    val gateLengthTicks: Int,
    val velocity: Int,
)

data class Stp116SequencerConfig(
    val midiChannel: Int,
    val sequenceLength: Int,
    val clockDivider: Int,
    val playbackMode: Stp116PlaybackMode,
    val bernoulliDropChance: Float,
    val randomGateLengthAmount: Float,
    val randomVelocityAmount: Float,
    val steps: List<Stp116StepConfig>,
)

fun stp116MidiNoteFromPitch(
    pitchClass: Int,
    octave: Int,
): Int {
    val safePitchClass = pitchClass.coerceIn(STP_116_MIN_PITCH_CLASS, STP_116_MAX_PITCH_CLASS)
    val safeOctave = octave.coerceIn(STP_116_MIN_OCTAVE, STP_116_MAX_OCTAVE)
    return (((safeOctave + 1) * 12) + safePitchClass).coerceIn(0, 127)
}

fun Stp116SequencerUiState.toConfig(): Stp116SequencerConfig {
    val normalized = normalized()
    return Stp116SequencerConfig(
        midiChannel = normalized.midiChannel,
        sequenceLength = normalized.sequenceLength,
        clockDivider = normalized.clockDivider,
        playbackMode = normalized.playbackMode,
        bernoulliDropChance = normalized.bernoulliDropChance,
        randomGateLengthAmount = normalized.randomGateLengthAmount,
        randomVelocityAmount = normalized.randomVelocityAmount,
        steps = normalized.steps.map { step ->
            Stp116StepConfig(
                enabled = step.enabled,
                pitchClass = step.pitchClass,
                octave = step.octave,
                midiNote = stp116MidiNoteFromPitch(step.pitchClass, step.octave),
                fineTuneCents = step.fineTuneCents,
                gateDelayTicks = step.gateDelayTicks,
                gateLengthTicks = step.gateLengthTicks,
                velocity = step.velocity,
            )
        },
    )
}
