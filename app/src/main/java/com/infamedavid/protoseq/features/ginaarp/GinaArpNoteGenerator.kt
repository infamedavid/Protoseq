package com.infamedavid.protoseq.features.ginaarp

import kotlin.math.roundToInt
import kotlin.random.Random

data class GinaArpGeneratedNote(
    val midiNote: Int,
    val velocity: Int,
    val stepIndex: Int,
    val divisionIndex: Int,
) {
    init {
        require(midiNote in 0..127) { "midiNote must be in 0..127" }
        require(velocity in GINA_ARP_MIN_VELOCITY..GINA_ARP_MAX_VELOCITY) { "velocity must be in 1..127" }
    }
}

fun GinaArpMode.scaleIntervals(): List<Int> =
    when (this) {
        GinaArpMode.MAJOR -> listOf(0, 2, 4, 5, 7, 9, 11)
        GinaArpMode.MINOR -> listOf(0, 2, 3, 5, 7, 8, 10)
        GinaArpMode.DORIAN -> listOf(0, 2, 3, 5, 7, 9, 10)
        GinaArpMode.MAJOR_PENTATONIC -> listOf(0, 2, 4, 7, 9)
        GinaArpMode.MINOR_PENTATONIC -> listOf(0, 3, 5, 7, 10)
    }

fun resolveGinaArpStepRootMidiNote(
    state: GinaArpSequencerUiState,
    step: GinaArpStepState,
): Int {
    val normalizedState = state.normalized()
    val normalizedStep = step.normalized()
    val intervals = normalizedState.mode.scaleIntervals()

    val degreeIndex = (normalizedStep.degree - 1).coerceAtLeast(0)
    val scaleSize = intervals.size
    val octaveWrap = degreeIndex / scaleSize
    val intervalInScale = intervals[degreeIndex % scaleSize]
    val degreeOffset = intervalInScale + (octaveWrap * 12)

    val midi = ((normalizedStep.octave + 1) * 12) + normalizedState.keyRootSemitone + degreeOffset
    return midi.coerceIn(0, 127)
}

enum class GinaArpRegisterZone {
    ZONE_0_ROOT_OCTAVE,
    ZONE_1_FIFTH,
    ZONE_2_THIRD,
    ZONE_3_SIXTH_SEVENTH,
    ZONE_4_FOURTH_SECOND,
    ZONE_5_FULL_SCALE,
    ZONE_6_CHROMATIC,
}

fun ginaArpRegisterZoneForMidiNote(midiNote: Int): GinaArpRegisterZone {
    val clampedMidi = midiNote.coerceIn(0, 127)
    return when {
        clampedMidi < 36 -> GinaArpRegisterZone.ZONE_0_ROOT_OCTAVE
        clampedMidi < 48 -> GinaArpRegisterZone.ZONE_1_FIFTH
        clampedMidi < 60 -> GinaArpRegisterZone.ZONE_2_THIRD
        clampedMidi < 72 -> GinaArpRegisterZone.ZONE_3_SIXTH_SEVENTH
        clampedMidi < 84 -> GinaArpRegisterZone.ZONE_4_FOURTH_SECOND
        clampedMidi < 96 -> GinaArpRegisterZone.ZONE_5_FULL_SCALE
        else -> GinaArpRegisterZone.ZONE_6_CHROMATIC
    }
}

fun isGinaArpIntervalAllowedInZone(
    intervalClass: Int,
    mode: GinaArpMode,
    zone: GinaArpRegisterZone,
): Boolean {
    val interval = ((intervalClass % 12) + 12) % 12

    val modeThird = when (mode) {
        GinaArpMode.MAJOR,
        GinaArpMode.MAJOR_PENTATONIC,
        -> 4

        GinaArpMode.MINOR,
        GinaArpMode.DORIAN,
        GinaArpMode.MINOR_PENTATONIC,
        -> 3
    }

    val modeSixths = when (mode) {
        GinaArpMode.MAJOR,
        GinaArpMode.DORIAN,
        GinaArpMode.MAJOR_PENTATONIC,
        -> setOf(9)

        GinaArpMode.MINOR -> setOf(8)
        GinaArpMode.MINOR_PENTATONIC -> emptySet()
    }

    val modeSevenths = when (mode) {
        GinaArpMode.MAJOR -> setOf(11)
        GinaArpMode.MINOR,
        GinaArpMode.DORIAN,
        -> setOf(10)

        GinaArpMode.MAJOR_PENTATONIC,
        GinaArpMode.MINOR_PENTATONIC,
        -> emptySet()
    }

    val allowed = mutableSetOf(0)
    if (zone.ordinal >= GinaArpRegisterZone.ZONE_1_FIFTH.ordinal) {
        allowed += 7
    }
    if (zone.ordinal >= GinaArpRegisterZone.ZONE_2_THIRD.ordinal) {
        allowed += modeThird
    }
    if (zone.ordinal >= GinaArpRegisterZone.ZONE_3_SIXTH_SEVENTH.ordinal) {
        allowed += modeSixths
        allowed += modeSevenths
    }
    if (zone.ordinal >= GinaArpRegisterZone.ZONE_4_FOURTH_SECOND.ordinal) {
        allowed += 2
        allowed += 5
    }
    if (zone.ordinal >= GinaArpRegisterZone.ZONE_5_FULL_SCALE.ordinal) {
        allowed += mode.scaleIntervals()
        allowed += 1
        allowed += 6
    }
    if (zone == GinaArpRegisterZone.ZONE_6_CHROMATIC) {
        return true
    }

    return interval in allowed
}

fun ginaArpRangeSemitones(
    stepRatio: Float,
    globalRatioMultiplier: Float,
): Int {
    val effectiveRatio = effectiveGinaArpRatio(
        stepRatio = stepRatio,
        globalRatioOffset = globalRatioMultiplier,
    )
    return (48f * effectiveRatio).roundToInt()
}

fun effectiveGinaArpRatio(
    stepRatio: Float,
    globalRatioOffset: Float,
): Float =
    (stepRatio.coerceIn(0f, 1f) + globalRatioOffset.coerceIn(-1f, 1f))
        .coerceIn(0f, 1f)

data class GinaArpNoteCandidate(
    val midiNote: Int,
    val intervalClass: Int,
    val zone: GinaArpRegisterZone,
    val weight: Float,
)

fun ginaArpCandidateWeight(
    intervalClass: Int,
    zone: GinaArpRegisterZone,
    mode: GinaArpMode,
    effectiveRatio: Float,
): Float {
    val normalizedInterval = ((intervalClass % 12) + 12) % 12

    var weight = when (normalizedInterval) {
        0 -> 10f
        7 -> 8f
        3, 4 -> 6f
        8, 9, 10, 11 -> 4f
        5 -> 3f
        2 -> 3f
        1 -> 1f
        6 -> 1f
        else -> 1f
    }

    if (normalizedInterval in setOf(1, 2, 5, 6)) {
        weight *= (1f + effectiveRatio)
    }

    weight *= (1f + zone.ordinal * 0.10f)

    if (mode == GinaArpMode.MINOR_PENTATONIC && normalizedInterval == 9) {
        weight *= 0.9f
    }

    return weight.coerceAtLeast(0.01f)
}

fun generateGinaArpNoteCandidates(
    state: GinaArpSequencerUiState,
    step: GinaArpStepState,
): List<GinaArpNoteCandidate> {
    val normalizedState = state.normalized()
    val normalizedStep = step.normalized()

    val rootMidi = resolveGinaArpStepRootMidiNote(normalizedState, normalizedStep)
    val rangeSemitones = ginaArpRangeSemitones(normalizedStep.ratio, normalizedState.globalRatioMultiplier)
    val effectiveRatio = effectiveGinaArpRatio(
        stepRatio = normalizedStep.ratio,
        globalRatioOffset = normalizedState.globalRatioMultiplier,
    )

    val windowMin = (rootMidi - rangeSemitones).coerceIn(0, 127)
    val windowMax = (rootMidi + rangeSemitones).coerceIn(0, 127)

    val candidates = (windowMin..windowMax).mapNotNull { midiNote ->
        val intervalClass = ((midiNote - rootMidi) % 12 + 12) % 12
        val zone = ginaArpRegisterZoneForMidiNote(midiNote)
        if (!isGinaArpIntervalAllowedInZone(intervalClass, normalizedState.mode, zone)) {
            null
        } else {
            GinaArpNoteCandidate(
                midiNote = midiNote,
                intervalClass = intervalClass,
                zone = zone,
                weight = ginaArpCandidateWeight(
                    intervalClass = intervalClass,
                    zone = zone,
                    mode = normalizedState.mode,
                    effectiveRatio = effectiveRatio,
                ),
            )
        }
    }

    if (candidates.isNotEmpty()) {
        return candidates
    }

    return listOf(
        GinaArpNoteCandidate(
            midiNote = rootMidi,
            intervalClass = 0,
            zone = ginaArpRegisterZoneForMidiNote(rootMidi),
            weight = 10f,
        ),
    )
}

fun selectWeightedGinaArpCandidate(
    candidates: List<GinaArpNoteCandidate>,
    random: Random,
): GinaArpNoteCandidate {
    require(candidates.isNotEmpty()) { "candidates must not be empty" }

    val positiveCandidates = candidates.filter { it.weight > 0f }
    require(positiveCandidates.isNotEmpty()) { "candidates must contain at least one positive weight" }

    val totalWeight = positiveCandidates.sumOf { it.weight.toDouble() }.toFloat()
    var target = random.nextFloat() * totalWeight

    for (candidate in positiveCandidates) {
        target -= candidate.weight
        if (target <= 0f) {
            return candidate
        }
    }

    return positiveCandidates.last()
}

fun stableGinaArpRandomForPosition(
    state: GinaArpSequencerUiState,
    step: GinaArpStepState,
    stepIndex: Int,
    divisionIndex: Int,
): Random {
    val normalizedState = state.normalized()
    val normalizedStep = step.normalized()
    val rootMidi = resolveGinaArpStepRootMidiNote(normalizedState, normalizedStep)
    val ratioBucket = (normalizedStep.ratio.coerceIn(0f, 1f) * 100f).roundToInt()
    val globalRatioBucket = (normalizedState.globalRatioMultiplier.coerceIn(-1f, 1f) * 100f).roundToInt()

    fun mix(hash: Int, value: Int): Int {
        var h = hash xor (value + 0x9e3779b9.toInt() + (hash shl 6) + (hash ushr 2))
        h = h xor (h ushr 16)
        h *= 0x7feb352d
        h = h xor (h ushr 15)
        h *= 0x846ca68b.toInt()
        h = h xor (h ushr 16)
        return h
    }

    var hash = 0x1234abcd
    hash = mix(hash, normalizedState.seed)
    hash = mix(hash, stepIndex)
    hash = mix(hash, divisionIndex)
    hash = mix(hash, rootMidi)
    hash = mix(hash, ratioBucket)
    hash = mix(hash, normalizedState.mode.ordinal)
    hash = mix(hash, normalizedState.keyRootSemitone)
    hash = mix(hash, normalizedState.globalNoteOffset)
    hash = mix(hash, globalRatioBucket)

    return Random(hash)
}

fun generateGinaArpNote(
    state: GinaArpSequencerUiState,
    stepIndex: Int,
    divisionIndex: Int,
    random: Random = Random.Default,
): GinaArpGeneratedNote? {
    val normalizedState = state.normalized()
    if (stepIndex !in 0 until GINA_ARP_STEP_COUNT) {
        return null
    }

    val step = normalizedState.steps[stepIndex].normalized()
    if (!step.enabled) {
        return null
    }

    if (shouldGinaArpForceRoot(divisionIndex = divisionIndex, arpLength = step.arpLength)) {
        return generateGinaArpRootNote(
            state = normalizedState,
            stepIndex = stepIndex,
            divisionIndex = divisionIndex,
        )
    }

    val candidates = generateGinaArpNoteCandidates(normalizedState, step)
    val selectionRandom = if (normalizedState.seed == GINA_ARP_MUTABLE_SEED) {
        random
    } else {
        stableGinaArpRandomForPosition(
            state = normalizedState,
            step = step,
            stepIndex = stepIndex,
            divisionIndex = divisionIndex,
        )
    }

    val selected = selectWeightedGinaArpCandidate(candidates, selectionRandom)
    val finalMidi = (selected.midiNote + normalizedState.globalNoteOffset).coerceIn(0, 127)

    return GinaArpGeneratedNote(
        midiNote = finalMidi,
        velocity = step.velocity.coerceIn(GINA_ARP_MIN_VELOCITY, GINA_ARP_MAX_VELOCITY),
        stepIndex = stepIndex,
        divisionIndex = divisionIndex,
    )
}

fun shouldGinaArpForceRoot(
    divisionIndex: Int,
    arpLength: Int,
): Boolean {
    val normalizedArpLength = arpLength.coerceIn(GINA_ARP_MIN_ARP_LENGTH, GINA_ARP_MAX_ARP_LENGTH)
    if (divisionIndex <= 0) {
        return true
    }
    return divisionIndex % normalizedArpLength == 0
}

fun generateGinaArpRootNote(
    state: GinaArpSequencerUiState,
    stepIndex: Int,
    divisionIndex: Int,
): GinaArpGeneratedNote? {
    val normalizedState = state.normalized()
    if (stepIndex !in 0 until GINA_ARP_STEP_COUNT) {
        return null
    }
    val step = normalizedState.steps[stepIndex].normalized()
    if (!step.enabled) {
        return null
    }

    val rootMidi = resolveGinaArpStepRootMidiNote(normalizedState, step)
    val finalMidi = (rootMidi + normalizedState.globalNoteOffset).coerceIn(0, 127)
    return GinaArpGeneratedNote(
        midiNote = finalMidi,
        velocity = step.velocity,
        stepIndex = stepIndex,
        divisionIndex = divisionIndex,
    )
}
