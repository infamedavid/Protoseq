package com.infamedavid.protoseq.features.grid616

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grid616SequencerUiStateTest {

    @Test
    fun stepNormalizationClampsVelocityAndDelay() {
        val normalized = Grid616StepState(velocity = 999, delayTicks = -4).normalized()

        assertEquals(GRID_616_MAX_VELOCITY, normalized.velocity)
        assertEquals(GRID_616_MIN_DELAY_TICKS, normalized.delayTicks)
    }

    @Test
    fun trackNormalizationClampsNoteAndLengthAndPadsSteps() {
        val normalized = Grid616TrackState(
            note = 200,
            length = 0,
            steps = listOf(
                Grid616StepState(velocity = 0, delayTicks = 100),
                Grid616StepState(enabled = true, velocity = 64, delayTicks = 12),
            ),
        ).normalized()

        assertEquals(127, normalized.note)
        assertEquals(GRID_616_MIN_TRACK_LENGTH, normalized.length)
        assertEquals(GRID_616_MAX_STEPS, normalized.steps.size)
        assertEquals(GRID_616_MIN_VELOCITY, normalized.steps[0].velocity)
        assertEquals(GRID_616_MAX_DELAY_TICKS, normalized.steps[0].delayTicks)
        assertEquals(Grid616StepState(), normalized.steps.last())
    }

    @Test
    fun trackNormalizationTruncatesStepsToMax() {
        val normalized = Grid616TrackState(
            note = 36,
            steps = List(32) { index -> Grid616StepState(enabled = index % 2 == 0) },
        ).normalized()

        assertEquals(GRID_616_MAX_STEPS, normalized.steps.size)
    }

    @Test
    fun trackNormalizationPreservesPlaybackMode() {
        val normalized = Grid616TrackState(
            note = 36,
            playbackMode = Grid616PlaybackMode.RANDOM,
        ).normalized()

        assertEquals(Grid616PlaybackMode.RANDOM, normalized.playbackMode)
    }

    @Test
    fun sequencerNormalizationClampsChannelAndSwingAndPadsTracks() {
        val normalized = Grid616SequencerUiState(
            midiChannel = 20,
            swingAmount = 0.95f,
            tracks = listOf(
                Grid616TrackState(note = -5),
            ),
        ).normalized()

        assertEquals(16, normalized.midiChannel)
        assertEquals(0.75f, normalized.swingAmount)
        assertEquals(GRID_616_TRACK_COUNT, normalized.tracks.size)
        assertEquals(0, normalized.tracks.first().note)
    }

    @Test
    fun sequencerNormalizationTruncatesTracksToSix() {
        val normalized = Grid616SequencerUiState(
            tracks = List(10) { index -> Grid616TrackState(note = index) },
        ).normalized()

        assertEquals(GRID_616_TRACK_COUNT, normalized.tracks.size)
    }

    @Test
    fun defaultTracksUseExpectedDrumNotes() {
        val notes = defaultGrid616Tracks().map { it.note }

        assertEquals(listOf(24, 26, 30, 34, 36, 38), notes)
        assertEquals(GRID_616_TRACK_COUNT, notes.size)
        assertTrue(defaultGrid616Tracks().all { it.playbackMode == Grid616PlaybackMode.FORWARD })
        assertTrue(defaultGrid616Tracks().all { it.steps.size == GRID_616_MAX_STEPS })
    }
}
