package com.infamedavid.protoseq.features.ginaarp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GinaArpSequencerUiStateTest {

    @Test
    fun defaultStateIsValid() {
        val state = GinaArpSequencerUiState()

        assertEquals(8, state.sequenceLength)
        assertEquals(8, state.steps.size)
        assertEquals(1, state.seed)
        assertEquals(GinaArpMode.MAJOR, state.mode)
        assertEquals(GinaArpPlayMode.FORWARD, state.playMode)
        assertEquals(1, state.tempoDivisor)
        assertEquals(3, state.midiChannel)
        assertEquals(0f, state.globalRatioMultiplier)
        assertEquals(0.5f, state.gateLength)
        assertEquals(0f, state.randomGateLength)
        assertEquals(1f, state.bernoulliGate)
        assertEquals(1, state.steps.first().degree)
        assertEquals(3, state.steps.first().octave)
        assertEquals(0.25f, state.steps.first().ratio)
        assertEquals(4, state.steps.first().divisions)
        assertEquals(2, state.steps.first().arpLength)
        assertEquals(100, state.steps.first().velocity)
    }

    @Test
    fun defaultStepStateUsesMusicalStartingPoint() {
        val step = GinaArpStepState()

        assertEquals(1, step.degree)
        assertEquals(3, step.octave)
        assertEquals(0.25f, step.ratio)
        assertEquals(4, step.divisions)
        assertEquals(2, step.arpLength)
        assertEquals(100, step.velocity)
    }

    @Test
    fun defaultGinaArpStepsUseStepDefaults() {
        val steps = defaultGinaArpSteps()

        assertEquals(8, steps.size)
        steps.forEach { step ->
            assertEquals(1, step.degree)
            assertEquals(3, step.octave)
            assertEquals(0.25f, step.ratio)
            assertEquals(4, step.divisions)
            assertEquals(2, step.arpLength)
            assertEquals(100, step.velocity)
        }
    }

    @Test
    fun stepNormalizationClampsAllRanges() {
        val low = GinaArpStepState(
            degree = 0,
            octave = -1,
            ratio = -0.1f,
            divisions = 0,
            arpLength = 0,
            velocity = 0,
        ).normalized()

        assertEquals(1, low.degree)
        assertEquals(0, low.octave)
        assertEquals(0f, low.ratio)
        assertEquals(1, low.divisions)
        assertEquals(1, low.arpLength)
        assertEquals(1, low.velocity)

        val high = GinaArpStepState(
            degree = 9,
            octave = 9,
            ratio = 1.1f,
            divisions = 8,
            arpLength = 8,
            velocity = 128,
        ).normalized()

        assertEquals(8, high.degree)
        assertEquals(8, high.octave)
        assertEquals(1f, high.ratio)
        assertEquals(7, high.divisions)
        assertEquals(7, high.arpLength)
        assertEquals(127, high.velocity)
    }

    @Test
    fun mainStateNormalizationClampsAllRanges() {
        val low = GinaArpSequencerUiState(
            sequenceLength = 0,
            seed = 0,
            globalRatioMultiplier = -2f,
            globalNoteOffset = -99,
            tempoDivisor = 0,
            midiChannel = 0,
            gateLength = -0.2f,
            randomGateLength = -0.3f,
            bernoulliGate = -0.4f,
        ).normalized()

        assertEquals(1, low.sequenceLength)
        assertEquals(1, low.seed)
        assertEquals(-1f, low.globalRatioMultiplier)
        assertEquals(-12, low.globalNoteOffset)
        assertEquals(1, low.tempoDivisor)
        assertEquals(1, low.midiChannel)
        assertEquals(0f, low.gateLength)
        assertEquals(0f, low.randomGateLength)
        assertEquals(0f, low.bernoulliGate)

        val high = GinaArpSequencerUiState(
            sequenceLength = 9,
            seed = 101,
            globalRatioMultiplier = 2f,
            globalNoteOffset = 99,
            tempoDivisor = 9,
            midiChannel = 17,
            gateLength = 1.2f,
            randomGateLength = 1.3f,
            bernoulliGate = 1.4f,
        ).normalized()

        assertEquals(8, high.sequenceLength)
        assertEquals(100, high.seed)
        assertEquals(1f, high.globalRatioMultiplier)
        assertEquals(12, high.globalNoteOffset)
        assertEquals(8, high.tempoDivisor)
        assertEquals(16, high.midiChannel)
        assertEquals(1f, high.gateLength)
        assertEquals(1f, high.randomGateLength)
        assertEquals(1f, high.bernoulliGate)
    }

    @Test
    fun keyRootSemitoneNormalizationWrapsIntoRange() {
        assertEquals(0, GinaArpSequencerUiState(keyRootSemitone = 12).normalized().keyRootSemitone)
        assertEquals(1, GinaArpSequencerUiState(keyRootSemitone = 13).normalized().keyRootSemitone)
        assertEquals(11, GinaArpSequencerUiState(keyRootSemitone = -1).normalized().keyRootSemitone)
    }

    @Test
    fun stepsNormalizationPadsTruncatesAndNormalizesNestedSteps() {
        val fewer = GinaArpSequencerUiState(
            steps = listOf(
                GinaArpStepState(enabled = true, degree = 0, octave = 99, ratio = -1f, divisions = 99, velocity = -1),
            ),
        ).normalized()

        assertEquals(8, fewer.steps.size)
        assertTrue(fewer.steps[0].enabled)
        assertEquals(1, fewer.steps[0].degree)
        assertEquals(8, fewer.steps[0].octave)
        assertEquals(0f, fewer.steps[0].ratio)
        assertEquals(7, fewer.steps[0].divisions)
        assertEquals(2, fewer.steps[0].arpLength)
        assertEquals(1, fewer.steps[0].velocity)
        assertEquals(GinaArpStepState(), fewer.steps.last())

        val more = GinaArpSequencerUiState(
            steps = List(16) { index ->
                GinaArpStepState(enabled = index == 10)
            },
        ).normalized()

        assertEquals(8, more.steps.size)
        assertFalse(more.steps.any { it.enabled })
    }

    @Test
    fun updateStepUpdatesOnlyTargetIndexAndReturnsNormalizedState() {
        val state = GinaArpSequencerUiState(
            steps = List(8) { GinaArpStepState() },
        )

        val updated = state.updateStep(3) {
            it.copy(enabled = true, degree = 99)
        }

        assertNotSame(state.steps, updated.steps)
        assertTrue(updated.steps[3].enabled)
        assertEquals(8, updated.steps[3].degree)
        assertEquals(GinaArpStepState(), updated.steps[0])
        assertEquals(GinaArpStepState(), updated.steps[7])
    }

    @Test
    fun updateStepIsolationKeepsAllOtherDistinctStepsUnchanged() {
        val steps = List(8) { index ->
            GinaArpStepState(
                enabled = index % 2 == 0,
                degree = (index % 8) + 1,
                octave = index % 9,
                ratio = index / 7f,
                divisions = (index % 7) + 1,
                arpLength = ((index + 2) % 7) + 1,
                velocity = 50 + index
            )
        }
        val state = GinaArpSequencerUiState(steps = steps)

        val updated = state.updateStep(3) { it.copy(degree = 8, divisions = 7, velocity = 120) }

        updated.steps.forEachIndexed { index, step ->
            if (index == 3) {
                assertEquals(8, step.degree)
                assertEquals(7, step.divisions)
                assertEquals(120, step.velocity)
            } else {
                assertEquals(steps[index], step)
            }
        }
    }

    @Test
    fun updateStepWithInvalidIndexReturnsNormalizedStateWithoutCrashing() {
        val state = GinaArpSequencerUiState(sequenceLength = 0, steps = emptyList())

        val updatedNegative = state.updateStep(-1) { it.copy(enabled = true) }
        val updatedTooHigh = state.updateStep(999) { it.copy(enabled = true) }

        assertEquals(state.normalized(), updatedNegative)
        assertEquals(state.normalized(), updatedTooHigh)
    }

    @Test
    fun seedHelpersReflectMutableAndImmutableModes() {
        assertTrue(GinaArpSequencerUiState(seed = 1).isMutableSeed)
        assertFalse(GinaArpSequencerUiState(seed = 1).isImmutableSeed)

        assertFalse(GinaArpSequencerUiState(seed = 2).isMutableSeed)
        assertTrue(GinaArpSequencerUiState(seed = 2).isImmutableSeed)

        assertFalse(GinaArpSequencerUiState(seed = 100).isMutableSeed)
        assertTrue(GinaArpSequencerUiState(seed = 100).isImmutableSeed)
    }
}
