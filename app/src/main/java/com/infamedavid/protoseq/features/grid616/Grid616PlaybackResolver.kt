package com.infamedavid.protoseq.features.grid616

import kotlin.random.Random

fun resolveGrid616StepIndex(
    globalStepCounter: Long,
    length: Int,
    playbackMode: Grid616PlaybackMode,
    randomIndexProvider: ((Int) -> Int)? = null
): Int {
    val normalizedLength = length.coerceIn(GRID_616_MIN_TRACK_LENGTH, GRID_616_MAX_TRACK_LENGTH)
    return when (playbackMode) {
        Grid616PlaybackMode.FORWARD -> (globalStepCounter % normalizedLength).toInt()
        Grid616PlaybackMode.REVERSE -> {
            val forwardIndex = (globalStepCounter % normalizedLength).toInt()
            (normalizedLength - 1) - forwardIndex
        }

        Grid616PlaybackMode.PING_PONG -> {
            if (normalizedLength <= 1) {
                0
            } else {
                val cycleLength = (normalizedLength * 2) - 2
                val position = (globalStepCounter % cycleLength).toInt()
                if (position < normalizedLength) position else cycleLength - position
            }
        }

        Grid616PlaybackMode.RANDOM -> {
            val index = randomIndexProvider?.invoke(normalizedLength)
                ?: Random.nextInt(until = normalizedLength)
            index.coerceIn(0, normalizedLength - 1)
        }
    }
}
