package com.infamedavid.protoseq.features.stp116

import kotlin.random.Random

fun resolveStp116StepIndex(
    globalStepCounter: Long,
    length: Int,
    playbackMode: Stp116PlaybackMode,
    randomIndexProvider: ((Int) -> Int)? = null,
): Int {
    val normalizedLength = length.coerceIn(
        STP_116_MIN_SEQUENCE_LENGTH,
        STP_116_MAX_SEQUENCE_LENGTH,
    )

    return when (playbackMode) {
        Stp116PlaybackMode.FORWARD -> {
            (globalStepCounter % normalizedLength).toInt()
        }

        Stp116PlaybackMode.REVERSE -> {
            val forwardIndex = (globalStepCounter % normalizedLength).toInt()
            (normalizedLength - 1) - forwardIndex
        }

        Stp116PlaybackMode.PING_PONG -> {
            if (normalizedLength <= 1) {
                0
            } else {
                val cycleLength = (normalizedLength * 2) - 2
                val position = (globalStepCounter % cycleLength).toInt()
                if (position < normalizedLength) position else cycleLength - position
            }
        }

        Stp116PlaybackMode.RANDOM -> {
            val index = randomIndexProvider?.invoke(normalizedLength)
                ?: Random.nextInt(until = normalizedLength)
            index.coerceIn(0, normalizedLength - 1)
        }

        Stp116PlaybackMode.CENTRIC -> {
            val position = (globalStepCounter % normalizedLength).toInt()
            resolveStp116CentricIndex(position = position, length = normalizedLength)
        }
    }
}

fun resolveStp116CentricIndex(
    position: Int,
    length: Int,
): Int {
    val normalizedLength = length.coerceIn(
        STP_116_MIN_SEQUENCE_LENGTH,
        STP_116_MAX_SEQUENCE_LENGTH,
    )
    val normalizedPosition = position.floorMod(normalizedLength)
    val pairIndex = normalizedPosition / 2
    return if (normalizedPosition % 2 == 0) {
        pairIndex
    } else {
        normalizedLength - 1 - pairIndex
    }
}

private fun Int.floorMod(modulus: Int): Int {
    val result = this % modulus
    return if (result < 0) result + modulus else result
}

data class Stp116ClockDividerResult(
    val nextCounter: Int,
    val shouldAdvance: Boolean,
)

fun advanceStp116ClockDivider(
    currentCounter: Int,
    clockDivider: Int,
): Stp116ClockDividerResult {
    val safeDivider = clockDivider.coerceIn(
        STP_116_MIN_CLOCK_DIVIDER,
        STP_116_MAX_CLOCK_DIVIDER,
    )
    val safeCounter = currentCounter.coerceIn(0, safeDivider - 1)
    val nextCounter = (safeCounter + 1) % safeDivider

    return Stp116ClockDividerResult(
        nextCounter = nextCounter,
        shouldAdvance = nextCounter == 0,
    )
}
