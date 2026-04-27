package com.infamedavid.protoseq.features.stp116

import kotlin.test.Test
import kotlin.test.assertEquals

class Stp116PitchBendTest {
    @Test
    fun pitchBendCenterForZeroCents() {
        assertEquals(8192, stp116PitchBendValueFromCents(0))
    }

    @Test
    fun pitchBendHalfPositiveForPlus100CentsWithDefaultRange() {
        assertEquals(12288, stp116PitchBendValueFromCents(100))
    }

    @Test
    fun pitchBendHalfNegativeForMinus100CentsWithDefaultRange() {
        assertEquals(4096, stp116PitchBendValueFromCents(-100))
    }

    @Test
    fun pitchBendMaximumForPlus200CentsWithDefaultRange() {
        assertEquals(16383, stp116PitchBendValueFromCents(200))
    }

    @Test
    fun pitchBendMinimumForMinus200CentsWithDefaultRange() {
        assertEquals(0, stp116PitchBendValueFromCents(-200))
    }

    @Test
    fun pitchBendClampsAboveRange() {
        assertEquals(16383, stp116PitchBendValueFromCents(999))
    }

    @Test
    fun pitchBendClampsBelowRange() {
        assertEquals(0, stp116PitchBendValueFromCents(-999))
    }

    @Test
    fun invalidRangeFallsBackToOneSemitone() {
        assertEquals(16383, stp116PitchBendValueFromCents(100, pitchBendRangeSemitones = 0))
        assertEquals(0, stp116PitchBendValueFromCents(-100, pitchBendRangeSemitones = 0))
    }
}
