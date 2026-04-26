package com.infamedavid.protoseq.features.ginaarp

const val GINA_ARP_STEP_COUNT = 8
const val GINA_ARP_MIN_SEQUENCE_LENGTH = 1
const val GINA_ARP_MAX_SEQUENCE_LENGTH = 8

const val GINA_ARP_MIN_STEP_DIVISIONS = 1
const val GINA_ARP_MAX_STEP_DIVISIONS = 7

const val GINA_ARP_MIN_SEED = 1
const val GINA_ARP_MAX_SEED = 100
const val GINA_ARP_MUTABLE_SEED = 1

const val GINA_ARP_MIN_VELOCITY = 1
const val GINA_ARP_MAX_VELOCITY = 127

const val GINA_ARP_MIN_NOTE_OFFSET = -12
const val GINA_ARP_MAX_NOTE_OFFSET = 12

const val GINA_ARP_MIN_TEMPO_DIVISOR = 1
const val GINA_ARP_MAX_TEMPO_DIVISOR = 8

const val GINA_ARP_MIN_OCTAVE = 0
const val GINA_ARP_MAX_OCTAVE = 8

const val GINA_ARP_MIN_DEGREE = 1
const val GINA_ARP_MAX_DEGREE = 8

enum class GinaArpMode {
    MAJOR,
    MINOR,
    DORIAN,
    MAJOR_PENTATONIC,
    MINOR_PENTATONIC,
}

enum class GinaArpPlayMode {
    FORWARD,
    REVERSE,
    PING_PONG,
    RANDOM,
}

data class GinaArpStepState(
    val enabled: Boolean = false,
    val degree: Int = 1,
    val octave: Int = 3,
    val ratio: Float = 0.5f,
    val divisions: Int = 2,
    val velocity: Int = 100,
)

fun GinaArpStepState.normalized(): GinaArpStepState =
    copy(
        degree = degree.coerceIn(GINA_ARP_MIN_DEGREE, GINA_ARP_MAX_DEGREE),
        octave = octave.coerceIn(GINA_ARP_MIN_OCTAVE, GINA_ARP_MAX_OCTAVE),
        ratio = ratio.coerceIn(0f, 1f),
        divisions = divisions.coerceIn(GINA_ARP_MIN_STEP_DIVISIONS, GINA_ARP_MAX_STEP_DIVISIONS),
        velocity = velocity.coerceIn(GINA_ARP_MIN_VELOCITY, GINA_ARP_MAX_VELOCITY),
    )

fun defaultGinaArpSteps(): List<GinaArpStepState> =
    List(GINA_ARP_STEP_COUNT) { GinaArpStepState() }

data class GinaArpSequencerUiState(
    val sequenceLength: Int = GINA_ARP_MAX_SEQUENCE_LENGTH,
    val keyRootSemitone: Int = 0,
    val mode: GinaArpMode = GinaArpMode.MAJOR,
    val playMode: GinaArpPlayMode = GinaArpPlayMode.FORWARD,
    val seed: Int = GINA_ARP_MUTABLE_SEED,
    val globalRatioMultiplier: Float = 1f,
    val globalNoteOffset: Int = 0,
    val tempoDivisor: Int = GINA_ARP_MIN_TEMPO_DIVISOR,
    val gateLength: Float = 0.5f,
    val randomGateLength: Float = 0f,
    val bernoulliGate: Float = 1f,
    val steps: List<GinaArpStepState> = defaultGinaArpSteps(),
)

fun GinaArpSequencerUiState.normalized(): GinaArpSequencerUiState {
    val normalizedSteps = steps
        .map { it.normalized() }
        .take(GINA_ARP_STEP_COUNT)
        .let { existing ->
            existing + List((GINA_ARP_STEP_COUNT - existing.size).coerceAtLeast(0)) { GinaArpStepState() }
        }

    val normalizedKeyRootSemitone = ((keyRootSemitone % 12) + 12) % 12

    return copy(
        sequenceLength = sequenceLength.coerceIn(GINA_ARP_MIN_SEQUENCE_LENGTH, GINA_ARP_MAX_SEQUENCE_LENGTH),
        keyRootSemitone = normalizedKeyRootSemitone,
        seed = seed.coerceIn(GINA_ARP_MIN_SEED, GINA_ARP_MAX_SEED),
        globalRatioMultiplier = globalRatioMultiplier.coerceIn(-1f, 1f),
        globalNoteOffset = globalNoteOffset.coerceIn(GINA_ARP_MIN_NOTE_OFFSET, GINA_ARP_MAX_NOTE_OFFSET),
        tempoDivisor = tempoDivisor.coerceIn(GINA_ARP_MIN_TEMPO_DIVISOR, GINA_ARP_MAX_TEMPO_DIVISOR),
        gateLength = gateLength.coerceIn(0f, 1f),
        randomGateLength = randomGateLength.coerceIn(0f, 1f),
        bernoulliGate = bernoulliGate.coerceIn(0f, 1f),
        steps = normalizedSteps,
    )
}

fun GinaArpSequencerUiState.updateStep(
    index: Int,
    transform: (GinaArpStepState) -> GinaArpStepState,
): GinaArpSequencerUiState {
    val normalizedState = normalized()
    if (index !in normalizedState.steps.indices) {
        return normalizedState
    }

    val updatedSteps = normalizedState.steps.toMutableList().apply {
        this[index] = transform(this[index])
    }

    return normalizedState.copy(steps = updatedSteps).normalized()
}

val GinaArpSequencerUiState.isMutableSeed: Boolean
    get() = seed == GINA_ARP_MUTABLE_SEED

val GinaArpSequencerUiState.isImmutableSeed: Boolean
    get() = seed != GINA_ARP_MUTABLE_SEED
