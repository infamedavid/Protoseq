package com.infamedavid.protoseq.features.ginaarp

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GinaArpNoteGeneratorTest {

    @Test
    fun shouldForceRootMatchesDivisionAndArpLengthRules() {
        assertTrue(shouldGinaArpForceRoot(divisionIndex = 0, arpLength = 3))
        assertTrue(shouldGinaArpForceRoot(divisionIndex = -5, arpLength = 3))

        assertTrue(shouldGinaArpForceRoot(divisionIndex = 0, arpLength = 3))
        assertTrue(shouldGinaArpForceRoot(divisionIndex = 3, arpLength = 3))
        assertTrue(shouldGinaArpForceRoot(divisionIndex = 6, arpLength = 3))
        assertFalse(shouldGinaArpForceRoot(divisionIndex = 1, arpLength = 3))
        assertFalse(shouldGinaArpForceRoot(divisionIndex = 2, arpLength = 3))
        assertFalse(shouldGinaArpForceRoot(divisionIndex = 4, arpLength = 3))
        assertFalse(shouldGinaArpForceRoot(divisionIndex = 5, arpLength = 3))

        assertTrue(shouldGinaArpForceRoot(divisionIndex = 0, arpLength = 4))
        assertTrue(shouldGinaArpForceRoot(divisionIndex = 4, arpLength = 4))
        assertFalse(shouldGinaArpForceRoot(divisionIndex = 1, arpLength = 4))
        assertFalse(shouldGinaArpForceRoot(divisionIndex = 2, arpLength = 4))
        assertFalse(shouldGinaArpForceRoot(divisionIndex = 3, arpLength = 4))

        assertTrue(shouldGinaArpForceRoot(divisionIndex = 7, arpLength = 0))
        assertTrue(shouldGinaArpForceRoot(divisionIndex = 7, arpLength = 99))
    }

    @Test
    fun scaleIntervalsMatchExpectedModes() {
        assertEquals(listOf(0, 2, 4, 5, 7, 9, 11), GinaArpMode.MAJOR.scaleIntervals())
        assertEquals(listOf(0, 2, 3, 5, 7, 8, 10), GinaArpMode.MINOR.scaleIntervals())
        assertEquals(listOf(0, 2, 3, 5, 7, 9, 10), GinaArpMode.DORIAN.scaleIntervals())
        assertEquals(listOf(0, 2, 4, 7, 9), GinaArpMode.MAJOR_PENTATONIC.scaleIntervals())
        assertEquals(listOf(0, 3, 5, 7, 10), GinaArpMode.MINOR_PENTATONIC.scaleIntervals())
    }

    @Test
    fun rootMidiResolutionMatchesExamples() {
        val cMajor = GinaArpSequencerUiState(keyRootSemitone = 0, mode = GinaArpMode.MAJOR)
        assertEquals(48, resolveGinaArpStepRootMidiNote(cMajor, GinaArpStepState(enabled = true, octave = 3, degree = 1)))
        assertEquals(52, resolveGinaArpStepRootMidiNote(cMajor, GinaArpStepState(enabled = true, octave = 3, degree = 3)))
        assertEquals(55, resolveGinaArpStepRootMidiNote(cMajor, GinaArpStepState(enabled = true, octave = 3, degree = 5)))
        assertEquals(60, resolveGinaArpStepRootMidiNote(cMajor, GinaArpStepState(enabled = true, octave = 3, degree = 8)))

        val cSharpMinor = GinaArpSequencerUiState(keyRootSemitone = 1, mode = GinaArpMode.MINOR)
        assertEquals(52, resolveGinaArpStepRootMidiNote(cSharpMinor, GinaArpStepState(enabled = true, octave = 3, degree = 3)))

        val dDorian = GinaArpSequencerUiState(keyRootSemitone = 2, mode = GinaArpMode.DORIAN)
        assertEquals(64, resolveGinaArpStepRootMidiNote(dDorian, GinaArpStepState(enabled = true, octave = 4, degree = 2)))
    }

    @Test
    fun registerZoneBoundariesAreCorrect() {
        assertEquals(GinaArpRegisterZone.ZONE_0_ROOT_OCTAVE, ginaArpRegisterZoneForMidiNote(35))
        assertEquals(GinaArpRegisterZone.ZONE_1_FIFTH, ginaArpRegisterZoneForMidiNote(36))
        assertEquals(GinaArpRegisterZone.ZONE_2_THIRD, ginaArpRegisterZoneForMidiNote(48))
        assertEquals(GinaArpRegisterZone.ZONE_3_SIXTH_SEVENTH, ginaArpRegisterZoneForMidiNote(60))
        assertEquals(GinaArpRegisterZone.ZONE_4_FOURTH_SECOND, ginaArpRegisterZoneForMidiNote(72))
        assertEquals(GinaArpRegisterZone.ZONE_5_FULL_SCALE, ginaArpRegisterZoneForMidiNote(84))
        assertEquals(GinaArpRegisterZone.ZONE_6_CHROMATIC, ginaArpRegisterZoneForMidiNote(96))
    }

    @Test
    fun intervalPermissionRulesMatchZonePolicy() {
        assertTrue(isGinaArpIntervalAllowedInZone(0, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_0_ROOT_OCTAVE))
        assertTrue(!isGinaArpIntervalAllowedInZone(7, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_0_ROOT_OCTAVE))
        assertTrue(!isGinaArpIntervalAllowedInZone(2, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_0_ROOT_OCTAVE))

        assertTrue(isGinaArpIntervalAllowedInZone(7, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_1_FIFTH))
        assertTrue(!isGinaArpIntervalAllowedInZone(4, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_1_FIFTH))

        assertTrue(isGinaArpIntervalAllowedInZone(4, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_2_THIRD))
        assertTrue(!isGinaArpIntervalAllowedInZone(3, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_2_THIRD))
        assertTrue(isGinaArpIntervalAllowedInZone(3, GinaArpMode.MINOR, GinaArpRegisterZone.ZONE_2_THIRD))

        assertTrue(isGinaArpIntervalAllowedInZone(2, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_4_FOURTH_SECOND))
        assertTrue(isGinaArpIntervalAllowedInZone(5, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_4_FOURTH_SECOND))

        assertTrue(isGinaArpIntervalAllowedInZone(1, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_5_FULL_SCALE))
        assertTrue(isGinaArpIntervalAllowedInZone(6, GinaArpMode.MAJOR, GinaArpRegisterZone.ZONE_5_FULL_SCALE))

        (0..11).forEach { interval ->
            assertTrue(isGinaArpIntervalAllowedInZone(interval, GinaArpMode.MINOR, GinaArpRegisterZone.ZONE_6_CHROMATIC))
        }
    }

    @Test
    fun rangeSemitonesUsesAdditiveNeutralRatioOffset() {
        assertEquals(24, ginaArpRangeSemitones(0.5f, 0f))
        assertEquals(36, ginaArpRangeSemitones(0.5f, 0.25f))
        assertEquals(12, ginaArpRangeSemitones(0.5f, -0.25f))
        assertEquals(0, ginaArpRangeSemitones(0f, -1f))
        assertEquals(48, ginaArpRangeSemitones(1f, 1f))
    }

    @Test
    fun candidateGenerationProducesExpectedShape() {
        val lowRatioStep = GinaArpStepState(enabled = true, octave = 3, degree = 1, ratio = 0.05f)
        val highRatioStep = lowRatioStep.copy(ratio = 1f)
        val cMajor = GinaArpSequencerUiState(keyRootSemitone = 0, mode = GinaArpMode.MAJOR, globalRatioMultiplier = 1f)

        val lowCandidates = generateGinaArpNoteCandidates(cMajor, lowRatioStep)
        val highCandidates = generateGinaArpNoteCandidates(cMajor, highRatioStep)

        assertTrue(lowCandidates.isNotEmpty())
        assertTrue(highCandidates.isNotEmpty())
        assertTrue(highCandidates.size > lowCandidates.size)

        assertTrue(lowCandidates.none { it.midiNote == 49 })
        assertTrue(highCandidates.any { it.intervalClass == 1 && it.zone.ordinal >= GinaArpRegisterZone.ZONE_5_FULL_SCALE.ordinal })

        val c4Step = GinaArpStepState(enabled = true, octave = 4, degree = 1, ratio = 1f)
        val c4Candidates = generateGinaArpNoteCandidates(cMajor, c4Step)
        assertTrue(c4Candidates.any { it.intervalClass == 2 && it.midiNote >= 72 })
    }

    @Test
    fun mutableSeedUsesPassedRandom() {
        val state = mutableStateForStep(0, seed = GINA_ARP_MUTABLE_SEED, arpLength = 7)
        val randomA = Random(101)
        val randomB = Random(202)

        val notesA = (0 until 20).mapNotNull { division ->
            generateGinaArpNote(state, stepIndex = 0, divisionIndex = division, random = randomA)?.midiNote
        }
        val notesB = (0 until 20).mapNotNull { division ->
            generateGinaArpNote(state, stepIndex = 0, divisionIndex = division, random = randomB)?.midiNote
        }

        assertNotEquals(notesA, notesB)
    }

    @Test
    fun immutableSeedIsDeterministicAndSensitiveToInputs() {
        val seed2State = mutableStateForStep(0, seed = 2, arpLength = 7)
        val seed3State = mutableStateForStep(0, seed = 3, arpLength = 7)

        val noteA = generateGinaArpNote(seed2State, stepIndex = 0, divisionIndex = 7, random = Random(1))
        val noteB = generateGinaArpNote(seed2State, stepIndex = 0, divisionIndex = 7, random = Random(999))
        assertEquals(noteA, noteB)

        val seed2Series = (0 until 16).mapNotNull { division ->
            generateGinaArpNote(seed2State, stepIndex = 0, divisionIndex = division, random = Random(5))?.midiNote
        }
        val seed3Series = (0 until 16).mapNotNull { division ->
            generateGinaArpNote(seed3State, stepIndex = 0, divisionIndex = division, random = Random(5))?.midiNote
        }
        assertTrue(seed2Series != seed3Series)

        val division1 = generateGinaArpNote(seed2State, stepIndex = 0, divisionIndex = 1)
        val division2 = generateGinaArpNote(seed2State, stepIndex = 0, divisionIndex = 2)
        assertNotEquals(division1?.midiNote, division2?.midiNote)

        val ratioChangedState = seed2State.updateStep(0) { it.copy(ratio = 0.33f) }
        val ratioChangedNote = generateGinaArpNote(ratioChangedState, stepIndex = 0, divisionIndex = 1)
        assertNotEquals(division1?.midiNote, ratioChangedNote?.midiNote)
    }

    @Test
    fun rootNoteGenerationRespectsStepStateOffsetVelocityAndClamp() {
        val disabled = GinaArpSequencerUiState()
        assertNull(generateGinaArpRootNote(disabled, stepIndex = 0, divisionIndex = 0))
        assertNull(generateGinaArpRootNote(disabled, stepIndex = -1, divisionIndex = 0))
        assertNull(generateGinaArpRootNote(disabled, stepIndex = 8, divisionIndex = 0))

        val enabled = mutableStateForStep(stepIndex = 0, seed = 2).copy(globalNoteOffset = 12)
        val root = generateGinaArpRootNote(enabled, stepIndex = 0, divisionIndex = 5)
        val expectedRoot = resolveGinaArpStepRootMidiNote(enabled.normalized(), enabled.normalized().steps[0]) + 12
        assertNotNull(root)
        assertEquals(expectedRoot.coerceIn(0, 127), root!!.midiNote)
        assertEquals(95, root.velocity)
        assertEquals(5, root.divisionIndex)

        val lowClampState = mutableStateForStep(
            stepIndex = 0,
            seed = 2,
            octave = 0,
            degree = 1,
        ).copy(keyRootSemitone = 0, globalNoteOffset = -12)
        assertEquals(0, generateGinaArpRootNote(lowClampState, 0, 0)!!.midiNote)

        val highClampState = mutableStateForStep(
            stepIndex = 0,
            seed = 2,
            octave = 8,
            degree = 8,
        ).copy(keyRootSemitone = 11, globalNoteOffset = 12)
        assertEquals(127, generateGinaArpRootNote(highClampState, 0, 0)!!.midiNote)
    }

    @Test
    fun generateNoteForcesRootOnCycleBoundariesAndBypassesRandomSelection() {
        val state = mutableStateForStep(stepIndex = 0, seed = GINA_ARP_MUTABLE_SEED, arpLength = 3)
        val resolvedRoot = resolveGinaArpStepRootMidiNote(state.normalized(), state.normalized().steps[0])

        val division0A = generateGinaArpNote(state, 0, 0, random = Random(1))
        val division0B = generateGinaArpNote(state, 0, 0, random = Random(999))
        val division3A = generateGinaArpNote(state, 0, 3, random = Random(2))
        val division3B = generateGinaArpNote(state, 0, 3, random = Random(333))

        assertEquals(resolvedRoot, division0A!!.midiNote)
        assertEquals(division0A, division0B)
        assertEquals(resolvedRoot, division3A!!.midiNote)
        assertEquals(division3A, division3B)

        val nonRootA = generateGinaArpNote(state, 0, 1, random = Random(4))
        val nonRootB = generateGinaArpNote(state, 0, 1, random = Random(8))
        assertNotNull(nonRootA)
        assertNotNull(nonRootB)
        assertNotEquals(nonRootA, nonRootB)
    }

    @Test
    fun generatedNoteHandlesNullabilityAndClampingRules() {
        val disabledState = GinaArpSequencerUiState()
        assertNull(generateGinaArpNote(disabledState, stepIndex = 0, divisionIndex = 0))

        val enabledState = mutableStateForStep(0, seed = 1)
        assertNull(generateGinaArpNote(enabledState, stepIndex = -1, divisionIndex = 0))
        assertNull(generateGinaArpNote(enabledState, stepIndex = 8, divisionIndex = 0))

        val generated = generateGinaArpNote(enabledState, stepIndex = 0, divisionIndex = 0, random = Random(42))
        assertNotNull(generated)
        assertTrue(generated!!.midiNote in 0..127)
        assertEquals(95, generated.velocity)

        val velocityClamped = generateGinaArpNote(
            enabledState.updateStep(0) { it.copy(velocity = 999) },
            stepIndex = 0,
            divisionIndex = 0,
        )
        assertEquals(127, velocityClamped!!.velocity)

        val offsetUpState = enabledState.copy(globalNoteOffset = 12)
        val offsetDownState = enabledState.copy(globalNoteOffset = -12)
        val base = generateGinaArpNote(enabledState, stepIndex = 0, divisionIndex = 3)
        val up = generateGinaArpNote(offsetUpState, stepIndex = 0, divisionIndex = 3)
        val down = generateGinaArpNote(offsetDownState, stepIndex = 0, divisionIndex = 3)

        assertEquals((base!!.midiNote + 12).coerceIn(0, 127), up!!.midiNote)
        assertEquals((base.midiNote - 12).coerceIn(0, 127), down!!.midiNote)
    }

    private fun mutableStateForStep(
        stepIndex: Int,
        seed: Int,
        arpLength: Int = 4,
        octave: Int = 4,
        degree: Int = 1,
    ): GinaArpSequencerUiState {
        val steps = List(GINA_ARP_STEP_COUNT) { GinaArpStepState() }.toMutableList()
        steps[stepIndex] = GinaArpStepState(
            enabled = true,
            degree = degree,
            octave = octave,
            ratio = 1f,
            arpLength = arpLength,
            velocity = 95,
        )

        return GinaArpSequencerUiState(
            keyRootSemitone = 0,
            mode = GinaArpMode.MAJOR,
            seed = seed,
            globalRatioMultiplier = 1f,
            globalNoteOffset = 0,
            steps = steps,
        )
    }
}
