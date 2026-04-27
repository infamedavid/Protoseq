package com.infamedavid.protoseq.features.stp116

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stp116PlaybackResolverTest {

    @Test
    fun forwardLengthFourResolvesZeroOneTwoThreeZero() {
        val resolved = (0L..4L).map { step ->
            resolveStp116StepIndex(step, length = 4, playbackMode = Stp116PlaybackMode.FORWARD)
        }

        assertEquals(listOf(0, 1, 2, 3, 0), resolved)
    }

    @Test
    fun reverseLengthFourResolvesThreeTwoOneZeroThree() {
        val resolved = (0L..4L).map { step ->
            resolveStp116StepIndex(step, length = 4, playbackMode = Stp116PlaybackMode.REVERSE)
        }

        assertEquals(listOf(3, 2, 1, 0, 3), resolved)
    }

    @Test
    fun pingPongLengthFourResolvesWithoutRepeatingEndpoints() {
        val resolved = (0L..6L).map { step ->
            resolveStp116StepIndex(step, length = 4, playbackMode = Stp116PlaybackMode.PING_PONG)
        }

        assertEquals(listOf(0, 1, 2, 3, 2, 1, 0), resolved)
    }

    @Test
    fun pingPongLengthOneAlwaysReturnsZero() {
        val resolved = (0L..16L).map { step ->
            resolveStp116StepIndex(step, length = 1, playbackMode = Stp116PlaybackMode.PING_PONG)
        }

        assertTrue(resolved.all { it == 0 })
    }

    @Test
    fun randomUsesProviderAndClampsInvalidOutput() {
        val resolved = resolveStp116StepIndex(
            globalStepCounter = 0L,
            length = 8,
            playbackMode = Stp116PlaybackMode.RANDOM,
            randomIndexProvider = { 99 },
        )

        assertEquals(7, resolved)
    }

    @Test
    fun centricLengthSixteenResolvesExpectedOrder() {
        val resolved = (0L..15L).map { step ->
            resolveStp116StepIndex(step, length = 16, playbackMode = Stp116PlaybackMode.CENTRIC)
        }

        assertEquals(listOf(0, 15, 1, 14, 2, 13, 3, 12, 4, 11, 5, 10, 6, 9, 7, 8), resolved)
    }

    @Test
    fun centricLengthFiveResolvesExpectedOrder() {
        val resolved = (0L..4L).map { step ->
            resolveStp116StepIndex(step, length = 5, playbackMode = Stp116PlaybackMode.CENTRIC)
        }

        assertEquals(listOf(0, 4, 1, 3, 2), resolved)
    }

    @Test
    fun dividerOneAdvancesEveryCall() {
        val a = advanceStp116ClockDivider(currentCounter = 0, clockDivider = 1)
        val b = advanceStp116ClockDivider(currentCounter = a.nextCounter, clockDivider = 1)

        assertTrue(a.shouldAdvance)
        assertTrue(b.shouldAdvance)
        assertEquals(0, a.nextCounter)
        assertEquals(0, b.nextCounter)
    }

    @Test
    fun dividerTwoAdvancesEverySecondCall() {
        val first = advanceStp116ClockDivider(currentCounter = 0, clockDivider = 2)
        val second = advanceStp116ClockDivider(currentCounter = first.nextCounter, clockDivider = 2)

        assertEquals(1, first.nextCounter)
        assertEquals(false, first.shouldAdvance)
        assertEquals(0, second.nextCounter)
        assertEquals(true, second.shouldAdvance)
    }

    @Test
    fun dividerFourAdvancesEveryFourthCall() {
        val one = advanceStp116ClockDivider(currentCounter = 0, clockDivider = 4)
        val two = advanceStp116ClockDivider(currentCounter = one.nextCounter, clockDivider = 4)
        val three = advanceStp116ClockDivider(currentCounter = two.nextCounter, clockDivider = 4)
        val four = advanceStp116ClockDivider(currentCounter = three.nextCounter, clockDivider = 4)

        assertEquals(false, one.shouldAdvance)
        assertEquals(false, two.shouldAdvance)
        assertEquals(false, three.shouldAdvance)
        assertEquals(true, four.shouldAdvance)
        assertEquals(0, four.nextCounter)
    }

    @Test
    fun invalidClockDividerValuesAreClampedSafely() {
        val low = advanceStp116ClockDivider(currentCounter = 5, clockDivider = -1)
        val high = advanceStp116ClockDivider(currentCounter = 99, clockDivider = 99)

        assertTrue(low.shouldAdvance)
        assertEquals(0, low.nextCounter)
        assertEquals(4, high.nextCounter)
        assertEquals(false, high.shouldAdvance)
    }
}
