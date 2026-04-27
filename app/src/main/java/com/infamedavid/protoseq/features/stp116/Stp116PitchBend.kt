package com.infamedavid.protoseq.features.stp116

import kotlin.math.roundToInt

const val STP_116_PITCH_BEND_MIN = 0
const val STP_116_PITCH_BEND_CENTER = 8192
const val STP_116_PITCH_BEND_MAX = 16383
const val STP_116_DEFAULT_PITCH_BEND_RANGE_SEMITONES = 2

fun stp116PitchBendValueFromCents(
    cents: Int,
    pitchBendRangeSemitones: Int = STP_116_DEFAULT_PITCH_BEND_RANGE_SEMITONES,
): Int {
    val safeRangeSemitones = pitchBendRangeSemitones.coerceAtLeast(1)
    val rangeCents = safeRangeSemitones * 100
    val safeCents = cents.coerceIn(-rangeCents, rangeCents)
    val normalized = safeCents.toFloat() / rangeCents.toFloat()

    return (STP_116_PITCH_BEND_CENTER + (normalized * STP_116_PITCH_BEND_CENTER))
        .roundToInt()
        .coerceIn(STP_116_PITCH_BEND_MIN, STP_116_PITCH_BEND_MAX)
}
