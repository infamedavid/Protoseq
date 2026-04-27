package com.infamedavid.protoseq.features.stp116

const val STP_116_STEP_COUNT = 16
const val STP_116_TICKS_PER_STEP = 24
const val STP_116_MIN_SEQUENCE_LENGTH = 1
const val STP_116_MAX_SEQUENCE_LENGTH = 16
const val STP_116_MIN_CLOCK_DIVIDER = 1
const val STP_116_MAX_CLOCK_DIVIDER = 16
const val STP_116_MIN_GATE_LENGTH_TICKS = 3
const val STP_116_MAX_GATE_DELAY_TICKS = 20
const val STP_116_MIN_PITCH_CLASS = 0
const val STP_116_MAX_PITCH_CLASS = 11
const val STP_116_MIN_OCTAVE = 0
const val STP_116_MAX_OCTAVE = 8
const val STP_116_MIN_FINE_TUNE_CENTS = -100
const val STP_116_MAX_FINE_TUNE_CENTS = 100
const val STP_116_MIN_VELOCITY = 1
const val STP_116_MAX_VELOCITY = 127

fun maxStp116GateLengthForDelay(delayTicks: Int): Int {
    val safeDelay = delayTicks.coerceIn(0, STP_116_MAX_GATE_DELAY_TICKS)
    return (STP_116_TICKS_PER_STEP - 1) - safeDelay
}

data class Stp116StepState(
    val enabled: Boolean = false,
    val pitchClass: Int = 0,
    val octave: Int = 4,
    val fineTuneCents: Int = 0,
    val gateDelayTicks: Int = 0,
    val gateLengthTicks: Int = 12,
    val velocity: Int = 100,
)

fun Stp116StepState.normalized(): Stp116StepState {
    val normalizedDelay = gateDelayTicks.coerceIn(0, STP_116_MAX_GATE_DELAY_TICKS)
    val maxGateLength = maxStp116GateLengthForDelay(normalizedDelay)

    return copy(
        pitchClass = pitchClass.coerceIn(STP_116_MIN_PITCH_CLASS, STP_116_MAX_PITCH_CLASS),
        octave = octave.coerceIn(STP_116_MIN_OCTAVE, STP_116_MAX_OCTAVE),
        fineTuneCents = fineTuneCents.coerceIn(
            STP_116_MIN_FINE_TUNE_CENTS,
            STP_116_MAX_FINE_TUNE_CENTS,
        ),
        gateDelayTicks = normalizedDelay,
        gateLengthTicks = gateLengthTicks.coerceIn(
            STP_116_MIN_GATE_LENGTH_TICKS,
            maxGateLength,
        ),
        velocity = velocity.coerceIn(STP_116_MIN_VELOCITY, STP_116_MAX_VELOCITY),
    )
}

fun defaultStp116Steps(): List<Stp116StepState> =
    List(STP_116_STEP_COUNT) { Stp116StepState() }

data class Stp116SequencerUiState(
    val midiChannel: Int = 1,
    val sequenceLength: Int = STP_116_STEP_COUNT,
    val clockDivider: Int = 1,
    val playbackMode: Stp116PlaybackMode = Stp116PlaybackMode.FORWARD,
    val bernoulliDropChance: Float = 0f,
    val randomGateLengthAmount: Float = 0f,
    val randomVelocityAmount: Float = 0f,
    val steps: List<Stp116StepState> = defaultStp116Steps(),
)

fun Stp116SequencerUiState.normalized(): Stp116SequencerUiState {
    val normalizedSteps = steps
        .map { it.normalized() }
        .take(STP_116_STEP_COUNT)
        .let { existing ->
            existing + List((STP_116_STEP_COUNT - existing.size).coerceAtLeast(0)) {
                Stp116StepState()
            }
        }

    return copy(
        midiChannel = midiChannel.coerceIn(1, 16),
        sequenceLength = sequenceLength.coerceIn(
            STP_116_MIN_SEQUENCE_LENGTH,
            STP_116_MAX_SEQUENCE_LENGTH,
        ),
        clockDivider = clockDivider.coerceIn(
            STP_116_MIN_CLOCK_DIVIDER,
            STP_116_MAX_CLOCK_DIVIDER,
        ),
        bernoulliDropChance = bernoulliDropChance.coerceIn(0f, 1f),
        randomGateLengthAmount = randomGateLengthAmount.coerceIn(0f, 1f),
        randomVelocityAmount = randomVelocityAmount.coerceIn(0f, 1f),
        steps = normalizedSteps,
    )
}

fun Stp116SequencerUiState.updateStep(
    stepIndex: Int,
    transform: (Stp116StepState) -> Stp116StepState,
): Stp116SequencerUiState {
    if (stepIndex !in 0 until STP_116_STEP_COUNT) return this
    val normalizedState = normalized()
    val updatedSteps = normalizedState.steps.toMutableList()
    updatedSteps[stepIndex] = transform(updatedSteps[stepIndex]).normalized()
    return normalizedState.copy(steps = updatedSteps).normalized()
}
