package com.infamedavid.protoseq.features.stp116

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class Stp116SequencerUiStateTest {

    @Test
    fun defaultStateHasSixteenSteps() {
        assertEquals(STP_116_STEP_COUNT, Stp116SequencerUiState().steps.size)
    }

    @Test
    fun normalizedMidiChannelClampsToOneThroughSixteen() {
        assertEquals(1, Stp116SequencerUiState(midiChannel = -1).normalized().midiChannel)
        assertEquals(16, Stp116SequencerUiState(midiChannel = 99).normalized().midiChannel)
    }

    @Test
    fun normalizedSequenceLengthClampsToOneThroughSixteen() {
        assertEquals(1, Stp116SequencerUiState(sequenceLength = 0).normalized().sequenceLength)
        assertEquals(16, Stp116SequencerUiState(sequenceLength = 99).normalized().sequenceLength)
    }

    @Test
    fun normalizedClockDividerClampsToOneThroughSixteen() {
        assertEquals(1, Stp116SequencerUiState(clockDivider = 0).normalized().clockDivider)
        assertEquals(16, Stp116SequencerUiState(clockDivider = 99).normalized().clockDivider)
    }

    @Test
    fun normalizedBernoulliDropChanceClampsToZeroThroughOne() {
        assertEquals(0f, Stp116SequencerUiState(bernoulliDropChance = -0.5f).normalized().bernoulliDropChance)
        assertEquals(1f, Stp116SequencerUiState(bernoulliDropChance = 2f).normalized().bernoulliDropChance)
    }

    @Test
    fun normalizedRandomGateLengthAmountClampsToZeroThroughOne() {
        assertEquals(0f, Stp116SequencerUiState(randomGateLengthAmount = -0.5f).normalized().randomGateLengthAmount)
        assertEquals(1f, Stp116SequencerUiState(randomGateLengthAmount = 2f).normalized().randomGateLengthAmount)
    }

    @Test
    fun normalizedRandomVelocityAmountClampsToZeroThroughOne() {
        assertEquals(0f, Stp116SequencerUiState(randomVelocityAmount = -0.5f).normalized().randomVelocityAmount)
        assertEquals(1f, Stp116SequencerUiState(randomVelocityAmount = 2f).normalized().randomVelocityAmount)
    }

    @Test
    fun stepPitchClassClampsToZeroThroughEleven() {
        assertEquals(0, Stp116StepState(pitchClass = -9).normalized().pitchClass)
        assertEquals(11, Stp116StepState(pitchClass = 99).normalized().pitchClass)
    }

    @Test
    fun stepOctaveClampsToZeroThroughEight() {
        assertEquals(0, Stp116StepState(octave = -1).normalized().octave)
        assertEquals(8, Stp116StepState(octave = 99).normalized().octave)
    }

    @Test
    fun stepFineTuneClampsToMinusHundredThroughHundred() {
        assertEquals(-100, Stp116StepState(fineTuneCents = -999).normalized().fineTuneCents)
        assertEquals(100, Stp116StepState(fineTuneCents = 999).normalized().fineTuneCents)
    }

    @Test
    fun stepVelocityClampsToOneThroughOneTwentySeven() {
        assertEquals(1, Stp116StepState(velocity = 0).normalized().velocity)
        assertEquals(127, Stp116StepState(velocity = 999).normalized().velocity)
    }

    @Test
    fun stepGateDelayClampsToZeroThroughTwenty() {
        assertEquals(0, Stp116StepState(gateDelayTicks = -1).normalized().gateDelayTicks)
        assertEquals(20, Stp116StepState(gateDelayTicks = 99).normalized().gateDelayTicks)
    }

    @Test
    fun stepGateLengthClampsAgainstDelayAwareBounds() {
        val low = Stp116StepState(gateDelayTicks = 20, gateLengthTicks = 1).normalized()
        val high = Stp116StepState(gateDelayTicks = 20, gateLengthTicks = 99).normalized()

        assertEquals(STP_116_MIN_GATE_LENGTH_TICKS, low.gateLengthTicks)
        assertEquals(maxStp116GateLengthForDelay(20), high.gateLengthTicks)
    }

    @Test
    fun shortStepListsArePaddedToSixteen() {
        val normalized = Stp116SequencerUiState(steps = listOf(Stp116StepState(enabled = true))).normalized()

        assertEquals(STP_116_STEP_COUNT, normalized.steps.size)
        assertEquals(true, normalized.steps.first().enabled)
        assertEquals(Stp116StepState(), normalized.steps.last())
    }

    @Test
    fun longStepListsAreTrimmedToSixteen() {
        val normalized = Stp116SequencerUiState(
            steps = List(64) { index -> Stp116StepState(enabled = index % 2 == 0) },
        ).normalized()

        assertEquals(STP_116_STEP_COUNT, normalized.steps.size)
    }

    @Test
    fun updateStepModifiesOnlyRequestedStep() {
        val state = Stp116SequencerUiState().normalized()

        val updated = state.updateStep(stepIndex = 2) { step ->
            step.copy(enabled = true, pitchClass = 7)
        }

        assertEquals(true, updated.steps[2].enabled)
        assertEquals(7, updated.steps[2].pitchClass)
        assertEquals(state.steps[1], updated.steps[1])
        assertEquals(state.steps[3], updated.steps[3])
    }

    @Test
    fun updateStepWithInvalidIndexReturnsSameState() {
        val state = Stp116SequencerUiState()

        val updated = state.updateStep(stepIndex = 99) { it.copy(enabled = true) }

        assertSame(state, updated)
    }

    @Test
    fun midiNoteFormulaMapsC4ToSixty() {
        assertEquals(60, stp116MidiNoteFromPitch(pitchClass = 0, octave = 4))
    }

    @Test
    fun toConfigReturnsNormalizedValuesAndSixteenStepConfigs() {
        val config = Stp116SequencerUiState(
            midiChannel = 100,
            sequenceLength = 99,
            clockDivider = 0,
            bernoulliDropChance = 2f,
            randomGateLengthAmount = -1f,
            randomVelocityAmount = 5f,
            steps = listOf(
                Stp116StepState(
                    enabled = true,
                    pitchClass = 99,
                    octave = 99,
                    fineTuneCents = 200,
                    gateDelayTicks = 99,
                    gateLengthTicks = 1,
                    velocity = 200,
                ),
            ),
        ).toConfig()

        assertEquals(16, config.midiChannel)
        assertEquals(16, config.sequenceLength)
        assertEquals(1, config.clockDivider)
        assertEquals(1f, config.bernoulliDropChance)
        assertEquals(0f, config.randomGateLengthAmount)
        assertEquals(1f, config.randomVelocityAmount)
        assertEquals(STP_116_STEP_COUNT, config.steps.size)
        assertEquals(11, config.steps.first().pitchClass)
        assertEquals(8, config.steps.first().octave)
        assertEquals(119, config.steps.first().midiNote)
    }
}
