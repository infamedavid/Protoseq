package com.infamedavid.protoseq.core.music

object ScaleQuantizer {
    private val majorDegrees = setOf(0, 2, 4, 5, 7, 9, 11)
    private val naturalMinorDegrees = setOf(0, 2, 3, 5, 7, 8, 10)
    private val pentatonicMajorDegrees = setOf(0, 2, 4, 7, 9)
    private val pentatonicMinorDegrees = setOf(0, 3, 5, 7, 10)
    private val dorianDegrees = setOf(0, 2, 3, 5, 7, 9, 10)
    private val phrygianDegrees = setOf(0, 1, 3, 5, 7, 8, 10)
    private val lydianDegrees = setOf(0, 2, 4, 6, 7, 9, 11)
    private val mixolydianDegrees = setOf(0, 2, 4, 5, 7, 9, 10)
    private val bluesDegrees = setOf(0, 3, 5, 6, 7, 10)
    private val harmonicMinorDegrees = setOf(0, 2, 3, 5, 7, 8, 11)
    private val melodicMinorDegrees = setOf(0, 2, 3, 5, 7, 9, 11)
    private val wholeToneDegrees = setOf(0, 2, 4, 6, 8, 10)
    private val diminishedDegrees = setOf(0, 2, 3, 5, 6, 8, 9, 11)

    fun quantize(note: Int, mode: QuantizationMode): Int {
        val clampedNote = note.coerceIn(0, 127)

        return when (mode) {
            QuantizationMode.NONE,
            QuantizationMode.CHRM -> clampedNote

            QuantizationMode.MAJR -> quantizeToScale(clampedNote, majorDegrees)
            QuantizationMode.MINR -> quantizeToScale(clampedNote, naturalMinorDegrees)
            QuantizationMode.PMAJ -> quantizeToScale(clampedNote, pentatonicMajorDegrees)
            QuantizationMode.PMIN -> quantizeToScale(clampedNote, pentatonicMinorDegrees)
            QuantizationMode.DOR -> quantizeToScale(clampedNote, dorianDegrees)
            QuantizationMode.PHR -> quantizeToScale(clampedNote, phrygianDegrees)
            QuantizationMode.LYD -> quantizeToScale(clampedNote, lydianDegrees)
            QuantizationMode.MIX -> quantizeToScale(clampedNote, mixolydianDegrees)
            QuantizationMode.BLUS -> quantizeToScale(clampedNote, bluesDegrees)
            QuantizationMode.HARM -> quantizeToScale(clampedNote, harmonicMinorDegrees)
            QuantizationMode.MELM -> quantizeToScale(clampedNote, melodicMinorDegrees)
            QuantizationMode.WHOL -> quantizeToScale(clampedNote, wholeToneDegrees)
            QuantizationMode.DIM -> quantizeToScale(clampedNote, diminishedDegrees)
        }
    }

    private fun quantizeToScale(note: Int, allowedDegrees: Set<Int>): Int {
        for (distance in 0..127) {
            val lower = note - distance
            if (lower >= 0 && (lower % 12) in allowedDegrees) {
                return lower
            }

            if (distance == 0) continue

            val upper = note + distance
            if (upper <= 127 && (upper % 12) in allowedDegrees) {
                return upper
            }
        }

        return note
    }
}
