package com.infamedavid.protoseq.features.grid616

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Grid616CrptStateTest {

    @Test
    fun defaultCrptStateHasSixEmptySlots() {
        val state = Grid616CrptState()

        assertEquals(0f, state.rndmAmount)
        assertEquals(GRID_616_CRPT_SLOT_COUNT, state.slots.size)
        assertTrue(state.slots.all { it.snapshot == null })
    }

    @Test
    fun slotNormalizationPadsAndTruncates() {
        val tooFew = Grid616CrptState(
            slots = listOf(Grid616CrptSlotState(snapshot = Grid616SequencerUiState().toCrptSnapshot())),
        ).normalized()
        val tooMany = Grid616CrptState(
            slots = List(GRID_616_CRPT_SLOT_COUNT + 3) { Grid616CrptSlotState() },
        ).normalized()

        assertEquals(GRID_616_CRPT_SLOT_COUNT, tooFew.slots.size)
        assertNotNull(tooFew.slots[0].snapshot)
        assertTrue(tooFew.slots.drop(1).all { it.snapshot == null })
        assertEquals(GRID_616_CRPT_SLOT_COUNT, tooMany.slots.size)
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

        val baseCrpt = Grid616CrptState(
            rndmAmount = 0.33f,
            slots = defaultGrid616CrptSlots().toMutableList().apply {
                this[0] = Grid616CrptSlotState(snapshot = snapshot)
            }
        )
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
    }

    @Test
    fun withSavedCrptSnapshotSavesOnlyTargetSlot() {
        val initialSnapshot = Grid616SequencerUiState().toCrptSnapshot()
        val state = Grid616SequencerUiState(
            crptState = Grid616CrptState(
                rndmAmount = 0.6f,
                slots = defaultGrid616CrptSlots().toMutableList().apply {
                    this[0] = Grid616CrptSlotState(snapshot = initialSnapshot)
                },
            )
        ).copy(
            tracks = defaultGrid616Tracks().map { it.copy(note = 88) }
        )

        val saved = state.withSavedCrptSnapshot(slotIndex = 2)

        assertEquals(0.6f, saved.crptState.rndmAmount)
        assertNotNull(saved.crptState.slots[2].snapshot)
        assertEquals(initialSnapshot, saved.crptState.slots[0].snapshot)
        assertTrue(saved.crptState.slots.filterIndexed { index, _ -> index !in listOf(0, 2) }.all { it.snapshot == null })
    }

    @Test
    fun withAppliedCrptSnapshotUsesGlobalRndmAndPreservesProtectedFields() {
        val snapshotSource = Grid616SequencerUiState(
            tracks = defaultGrid616Tracks().map { track ->
                track.copy(note = 40, length = GRID_616_MIN_TRACK_LENGTH)
            }
        )
        val state = Grid616SequencerUiState(
            midiChannel = 10,
            swingAmount = 0.42f,
            tracks = defaultGrid616Tracks().map { it.copy(muted = true, note = 90) },
            crptState = Grid616CrptState(
                rndmAmount = 1f,
                slots = defaultGrid616CrptSlots().toMutableList().apply {
                    this[2] = Grid616CrptSlotState(snapshot = snapshotSource.toCrptSnapshot())
                },
            ),
        )

        val applied = state.withAppliedCrptSnapshot(slotIndex = 2, random = Random(1))

        assertEquals(10, applied.midiChannel)
        assertEquals(0.42f, applied.swingAmount)
        assertTrue(applied.tracks.all { it.muted })
        assertEquals(state.crptState.normalized(), applied.crptState)
        assertNotEquals(90, applied.tracks.first().note)
    }

    @Test
    fun invalidSlotIndexReturnsNormalizedUnchangedState() {
        val state = Grid616SequencerUiState(
            midiChannel = 44,
            swingAmount = 3f,
            crptState = Grid616CrptState(),
        )

        assertEquals(state.normalized(), state.withSavedCrptSnapshot(slotIndex = -1))
        assertEquals(state.normalized(), state.withAppliedCrptSnapshot(slotIndex = GRID_616_CRPT_SLOT_COUNT, random = Random(99)))
    }

    @Test
    fun withAppliedCrptSnapshotWithEmptySlotReturnsNormalizedState() {
        val state = Grid616SequencerUiState(
            midiChannel = 44,
            swingAmount = 3f,
            crptState = Grid616CrptState(),
        )

        val applied = state.withAppliedCrptSnapshot(slotIndex = 0, random = Random(99))

        assertEquals(state.normalized(), applied)
        assertNull(applied.crptState.slots[0].snapshot)
    }
}
