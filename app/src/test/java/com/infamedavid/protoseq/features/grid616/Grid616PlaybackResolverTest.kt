package com.infamedavid.protoseq.features.grid616

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grid616PlaybackResolverTest {

    @Test
    fun forwardModeResolvesModuloLength() {
        val resolved = listOf(0L, 1L, 2L, 3L, 4L, 5L).map { step ->
            resolveGrid616StepIndex(step, length = 4, playbackMode = Grid616PlaybackMode.FORWARD)
        }

        assertEquals(listOf(0, 1, 2, 3, 0, 1), resolved)
    }

    @Test
    fun reverseModeResolvesInverseModuloLength() {
        val resolved = listOf(0L, 1L, 2L, 3L, 4L, 5L).map { step ->
            resolveGrid616StepIndex(step, length = 4, playbackMode = Grid616PlaybackMode.REVERSE)
        }

        assertEquals(listOf(3, 2, 1, 0, 3, 2), resolved)
    }

    @Test
    fun pingPongModeDoesNotRepeatEndpoints() {
        val resolved = (0L..7L).map { step ->
            resolveGrid616StepIndex(step, length = 4, playbackMode = Grid616PlaybackMode.PING_PONG)
        }

        assertEquals(listOf(0, 1, 2, 3, 2, 1, 0, 1), resolved)
    }

    @Test
    fun pingPongModeLengthOneAlwaysReturnsZero() {
        val resolved = (0L..15L).map { step ->
            resolveGrid616StepIndex(step, length = 1, playbackMode = Grid616PlaybackMode.PING_PONG)
        }

        assertTrue(resolved.all { it == 0 })
    }

    @Test
    fun differentLengthsDriftIndependently() {
        val length8 = (0L..15L).map { step ->
            resolveGrid616StepIndex(step, length = 8, playbackMode = Grid616PlaybackMode.FORWARD)
        }
        val length7 = (0L..15L).map { step ->
            resolveGrid616StepIndex(step, length = 7, playbackMode = Grid616PlaybackMode.FORWARD)
        }

        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7), length8)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6, 0, 1), length7)
    }

    @Test
    fun randomModeUsesProviderWithinRange() {
        val resolved = resolveGrid616StepIndex(
            globalStepCounter = 0L,
            length = 7,
            playbackMode = Grid616PlaybackMode.RANDOM,
            randomIndexProvider = { 99 }
        )

        assertEquals(6, resolved)
    }
}
