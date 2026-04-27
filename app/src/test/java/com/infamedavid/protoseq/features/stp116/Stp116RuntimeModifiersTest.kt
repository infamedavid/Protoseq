package com.infamedavid.protoseq.features.stp116

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stp116RuntimeModifiersTest {

    @Test
    fun dropChanceZeroAlwaysReturnsFalse() {
        assertFalse(shouldDropStp116Gate(0f) { 0f })
        assertFalse(shouldDropStp116Gate(0f) { 1f })
    }

    @Test
    fun dropChanceOneAlwaysReturnsTrue() {
        assertTrue(shouldDropStp116Gate(1f) { 0f })
        assertTrue(shouldDropStp116Gate(1f) { 1f })
    }

    @Test
    fun dropChanceHalfReturnsTrueWhenRandomBelowChance() {
        assertTrue(shouldDropStp116Gate(0.5f) { 0.25f })
    }

    @Test
    fun dropChanceHalfReturnsFalseWhenRandomAboveChance() {
        assertFalse(shouldDropStp116Gate(0.5f) { 0.75f })
    }

    @Test
    fun dropChanceBelowZeroIsClampedToZero() {
        assertFalse(shouldDropStp116Gate(-0.5f) { 0f })
    }

    @Test
    fun dropChanceAboveOneIsClampedToOne() {
        assertTrue(shouldDropStp116Gate(1.5f) { 1f })
    }

    @Test
    fun dropRandomProviderOutputIsClampedSafely() {
        assertTrue(shouldDropStp116Gate(0.5f) { -1f })
        assertFalse(shouldDropStp116Gate(0.5f) { 2f })
    }

    @Test
    fun velocityAmountZeroReturnsExactVelocity() {
        assertEquals(
            100,
            calculateStp116FinalVelocity(
                velocity = 100,
                randomVelocityAmount = 0f,
                randomIntProvider = { _, _ -> 64 },
            ),
        )
    }

    @Test
    fun velocityIsClampedToMidiRange() {
        assertEquals(
            STP_116_MIN_VELOCITY,
            calculateStp116FinalVelocity(
                velocity = -10,
                randomVelocityAmount = 0f,
                randomIntProvider = { _, _ -> 64 },
            ),
        )
        assertEquals(
            STP_116_MAX_VELOCITY,
            calculateStp116FinalVelocity(
                velocity = 999,
                randomVelocityAmount = 0f,
                randomIntProvider = { _, _ -> 64 },
            ),
        )
    }

    @Test
    fun velocityAmountBelowZeroBehavesLikeZero() {
        assertEquals(
            90,
            calculateStp116FinalVelocity(
                velocity = 90,
                randomVelocityAmount = -0.5f,
                randomIntProvider = { _, _ -> 1 },
            ),
        )
    }

    @Test
    fun velocityAmountAboveOneBehavesLikeOne() {
        var receivedMin = Int.MIN_VALUE
        var receivedMax = Int.MIN_VALUE

        calculateStp116FinalVelocity(
            velocity = 100,
            randomVelocityAmount = 1.5f,
            randomIntProvider = { minInclusive, maxInclusive ->
                receivedMin = minInclusive
                receivedMax = maxInclusive
                maxInclusive
            },
        )

        assertEquals(1, receivedMin)
        assertEquals(127, receivedMax)
    }

    @Test
    fun velocityAmountOneNeverReturnsOutsideMidiRange() {
        repeat(20) {
            val minValue = calculateStp116FinalVelocity(
                velocity = 1,
                randomVelocityAmount = 1f,
                randomIntProvider = { _, _ -> Int.MIN_VALUE },
            )
            val maxValue = calculateStp116FinalVelocity(
                velocity = 127,
                randomVelocityAmount = 1f,
                randomIntProvider = { _, _ -> Int.MAX_VALUE },
            )
            assertTrue(minValue in 1..127)
            assertTrue(maxValue in 1..127)
        }
    }

    @Test
    fun velocityRandomProviderReceivesExpectedMinAndMax() {
        var receivedMin = Int.MIN_VALUE
        var receivedMax = Int.MIN_VALUE

        calculateStp116FinalVelocity(
            velocity = 100,
            randomVelocityAmount = 0.5f,
            randomIntProvider = { minInclusive, maxInclusive ->
                receivedMin = minInclusive
                receivedMax = maxInclusive
                minInclusive
            },
        )

        assertEquals(50, receivedMin)
        assertEquals(127, receivedMax)
    }

    @Test
    fun velocityProviderOutputIsClampedSafely() {
        val low = calculateStp116FinalVelocity(
            velocity = 100,
            randomVelocityAmount = 1f,
            randomIntProvider = { _, _ -> -1000 },
        )
        val high = calculateStp116FinalVelocity(
            velocity = 100,
            randomVelocityAmount = 1f,
            randomIntProvider = { _, _ -> 9999 },
        )

        assertEquals(1, low)
        assertEquals(127, high)
    }

    @Test
    fun gateLengthAmountZeroReturnsProgrammedLength() {
        assertEquals(
            12L,
            calculateStp116FinalGateLengthTicks(
                gateLengthTicks = 12,
                gateDelayTicks = 0,
                randomGateLengthAmount = 0f,
                randomIntProvider = { _, _ -> 9 },
            ),
        )
    }

    @Test
    fun gateLengthClampsToMinimumThree() {
        assertEquals(
            3L,
            calculateStp116FinalGateLengthTicks(
                gateLengthTicks = 1,
                gateDelayTicks = 0,
                randomGateLengthAmount = 0f,
                randomIntProvider = { _, _ -> 10 },
            ),
        )
    }

    @Test
    fun gateLengthClampsToSafeMaximumForDelay() {
        val result = calculateStp116FinalGateLengthTicks(
            gateLengthTicks = 999,
            gateDelayTicks = 10,
            randomGateLengthAmount = 0f,
            randomIntProvider = { _, _ -> 10 },
        )

        assertEquals(maxStp116GateLengthForDelay(10).toLong(), result)
    }

    @Test
    fun delayTwentyGivesMaxGateThree() {
        assertEquals(3, maxStp116GateLengthForDelay(20))
    }

    @Test
    fun gateLengthAmountBelowZeroBehavesLikeZero() {
        assertEquals(
            14L,
            calculateStp116FinalGateLengthTicks(
                gateLengthTicks = 14,
                gateDelayTicks = 0,
                randomGateLengthAmount = -0.25f,
                randomIntProvider = { _, _ -> 3 },
            ),
        )
    }

    @Test
    fun gateLengthAmountAboveOneBehavesLikeOne() {
        var receivedMin = Int.MIN_VALUE
        var receivedMax = Int.MIN_VALUE

        calculateStp116FinalGateLengthTicks(
            gateLengthTicks = 12,
            gateDelayTicks = 0,
            randomGateLengthAmount = 1.25f,
            randomIntProvider = { minInclusive, maxInclusive ->
                receivedMin = minInclusive
                receivedMax = maxInclusive
                maxInclusive
            },
        )

        assertEquals(STP_116_MIN_GATE_LENGTH_TICKS, receivedMin)
        assertEquals(maxStp116GateLengthForDelay(0), receivedMax)
    }

    @Test
    fun gateLengthAmountOneNeverReturnsOutsideSafeRange() {
        repeat(20) {
            val low = calculateStp116FinalGateLengthTicks(
                gateLengthTicks = 10,
                gateDelayTicks = 20,
                randomGateLengthAmount = 1f,
                randomIntProvider = { _, _ -> Int.MIN_VALUE },
            )
            val high = calculateStp116FinalGateLengthTicks(
                gateLengthTicks = 10,
                gateDelayTicks = 20,
                randomGateLengthAmount = 1f,
                randomIntProvider = { _, _ -> Int.MAX_VALUE },
            )

            val safeMax = maxStp116GateLengthForDelay(20).toLong()
            assertTrue(low in 3L..safeMax)
            assertTrue(high in 3L..safeMax)
        }
    }

    @Test
    fun gateLengthRandomProviderReceivesExpectedMinAndMax() {
        var receivedMin = Int.MIN_VALUE
        var receivedMax = Int.MIN_VALUE

        calculateStp116FinalGateLengthTicks(
            gateLengthTicks = 12,
            gateDelayTicks = 0,
            randomGateLengthAmount = 0.5f,
            randomIntProvider = { minInclusive, maxInclusive ->
                receivedMin = minInclusive
                receivedMax = maxInclusive
                minInclusive
            },
        )

        assertEquals(3, receivedMin)
        assertEquals(22, receivedMax)
    }

    @Test
    fun gateLengthProviderOutputIsClampedSafely() {
        val low = calculateStp116FinalGateLengthTicks(
            gateLengthTicks = 12,
            gateDelayTicks = 0,
            randomGateLengthAmount = 1f,
            randomIntProvider = { _, _ -> Int.MIN_VALUE },
        )
        val high = calculateStp116FinalGateLengthTicks(
            gateLengthTicks = 12,
            gateDelayTicks = 0,
            randomGateLengthAmount = 1f,
            randomIntProvider = { _, _ -> Int.MAX_VALUE },
        )

        assertEquals(3L, low)
        assertEquals(maxStp116GateLengthForDelay(0).toLong(), high)
    }
}
