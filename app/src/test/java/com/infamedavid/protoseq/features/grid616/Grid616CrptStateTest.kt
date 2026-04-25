package com.infamedavid.protoseq.features.grid616

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Grid616CrptStateTest {

    @Test
    fun defaultCrptStateIsEmpty() {
        val state = Grid616SequencerUiState()

        assertEquals(0f, state.crptState.rndmAmount)
        assertNull(state.crptState.snapshot)
    }

    @Test
    fun rndmAmountClampsInNormalization() {
        val low = Grid616SequencerUiState(
            crptState = Grid616CrptState(rndmAmount = -1f),
        ).normalized()
        val high = Grid616SequencerUiState(
            crptState = Grid616CrptState(rndmAmount = 2f),
        ).normalized()

        assertEquals(0f, low.crptState.rndmAmount)
        assertEquals(1f, high.crptState.rndmAmount)
    }

    @Test
    fun toCrptSnapshotCapturesMusicalPattern() {
        val state = Grid616SequencerUiState(
            tracks = defaultGrid616Tracks().mapIndexed { trackIndex, track ->
                track.copy(
                    note = 60 + trackIndex,
                    length = 8 + (trackIndex % 4),
                    playbackMode = Grid616PlaybackMode.REVERSE,
                    steps = List(GRID_616_MAX_STEPS) { stepIndex ->
                        Grid616StepState(
                            enabled = stepIndex % 2 == 0,
                            velocity = 30 + stepIndex,
                            delayTicks = stepIndex % (GRID_616_MAX_DELAY_TICKS + 1),
                        )
                    },
                )
            },
        )

        val snapshot = state.toCrptSnapshot()
        val firstTrack = snapshot.tracks.first()
        val firstStep = firstTrack.steps.first()

        assertEquals(60, firstTrack.note)
        assertEquals(8, firstTrack.length)
        assertEquals(Grid616PlaybackMode.REVERSE, firstTrack.playbackMode)
        assertEquals(true, firstStep.enabled)
        assertEquals(30, firstStep.velocity)
        assertEquals(0, firstStep.delayTicks)
    }

    @Test
    fun toCrptSnapshotDoesNotCaptureMute() {
        val state = Grid616SequencerUiState(
            tracks = defaultGrid616Tracks().map { it.copy(muted = true) },
        )

        val snapshot = state.toCrptSnapshot()
        val restored = Grid616SequencerUiState(
            tracks = defaultGrid616Tracks().map { it.copy(muted = false) },
        ).applyCrptSnapshot(snapshot)

        assertTrue(restored.tracks.all { !it.muted })
    }

    @Test
    fun applyCrptSnapshotAppliesMusicalPatternAndPreservesNonSnapshotFields() {
        val snapshotSource = Grid616SequencerUiState(
            tracks = defaultGrid616Tracks().mapIndexed { index, track ->
                track.copy(
                    note = 70 + index,
                    length = GRID_616_MIN_TRACK_LENGTH + index,
                    playbackMode = Grid616PlaybackMode.RANDOM,
                    steps = List(GRID_616_MAX_STEPS) { stepIndex ->
                        Grid616StepState(
                            enabled = stepIndex % 3 == 0,
                            velocity = (10 + stepIndex).coerceAtMost(GRID_616_MAX_VELOCITY),
                            delayTicks = (stepIndex % (GRID_616_MAX_DELAY_TICKS + 1)),
                        )
                    },
                )
            },
        )
        val snapshot = snapshotSource.toCrptSnapshot()

        val baseCrpt = Grid616CrptState(rndmAmount = 0.33f, snapshot = snapshot)
        val target = Grid616SequencerUiState(
            midiChannel = 2,
            swingAmount = 0.5f,
            tracks = defaultGrid616Tracks().map { it.copy(muted = true) },
            crptState = baseCrpt,
        )

        val applied = target.applyCrptSnapshot(snapshot)

        assertEquals(2, applied.midiChannel)
        assertEquals(0.5f, applied.swingAmount)
        assertEquals(baseCrpt.normalized(), applied.crptState)
        assertTrue(applied.tracks.all { it.muted })
        assertEquals(70, applied.tracks.first().note)
        assertEquals(GRID_616_MIN_TRACK_LENGTH, applied.tracks.first().length)
        assertEquals(Grid616PlaybackMode.RANDOM, applied.tracks.first().playbackMode)
        assertEquals(true, applied.tracks.first().steps[0].enabled)
    }

    @Test
    fun applyCrptSnapshotWithMutationAtZeroMatchesExactApply() {
        val snapshot = Grid616SequencerUiState().toCrptSnapshot()
        val target = Grid616SequencerUiState(
            midiChannel = 15,
            swingAmount = 0.25f,
            tracks = defaultGrid616Tracks().map { it.copy(muted = true) },
            crptState = Grid616CrptState(rndmAmount = 0.5f, snapshot = snapshot),
        )

        val exact = target.applyCrptSnapshot(snapshot)
        val mutated = target.applyCrptSnapshotWithMutation(snapshot, 0f, Random(1234))

        assertEquals(exact, mutated)
    }

    @Test
    fun applyCrptSnapshotWithMutationAtOneStaysInValidRangesAndPreservesProtectedFields() {
        val snapshot = Grid616SequencerUiState().toCrptSnapshot()
        val target = Grid616SequencerUiState(
            midiChannel = 9,
            swingAmount = 0.4f,
            tracks = defaultGrid616Tracks().map { it.copy(muted = true) },
            crptState = Grid616CrptState(rndmAmount = 1f, snapshot = snapshot),
        )

        val mutated = target.applyCrptSnapshotWithMutation(snapshot, 1f, Random(0))

        assertEquals(9, mutated.midiChannel)
        assertEquals(0.4f, mutated.swingAmount)
        assertTrue(mutated.tracks.all { it.muted })
        mutated.tracks.forEach { track ->
            assertTrue(track.note in 0..127)
            assertTrue(track.length in GRID_616_MIN_TRACK_LENGTH..GRID_616_MAX_TRACK_LENGTH)
            assertTrue(Grid616PlaybackMode.entries.contains(track.playbackMode))
            track.steps.forEach { step ->
                assertTrue(step.velocity in GRID_616_MIN_VELOCITY..GRID_616_MAX_VELOCITY)
                assertTrue(step.delayTicks in GRID_616_MIN_DELAY_TICKS..GRID_616_MAX_DELAY_TICKS)
            }
        }
    }

    @Test
    fun withSavedCrptSnapshotStoresSnapshotAndPreservesRndmAmount() {
        val state = Grid616SequencerUiState(
            crptState = Grid616CrptState(rndmAmount = 0.6f),
        )

        val saved = state.withSavedCrptSnapshot()

        assertNotNull(saved.crptState.snapshot)
        assertEquals(0.6f, saved.crptState.rndmAmount)
    }

    @Test
    fun withAppliedCrptSnapshotWithNullSnapshotReturnsNormalizedState() {
        val state = Grid616SequencerUiState(
            midiChannel = 44,
            swingAmount = 3f,
            crptState = Grid616CrptState(snapshot = null),
        )

        val applied = state.withAppliedCrptSnapshot(Random(99))

        assertEquals(state.normalized(), applied)
    }
}
