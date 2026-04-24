package com.infamedavid.protoseq.features.sequencer

import com.infamedavid.protoseq.features.stochastic.StochasticSequencerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtoseqSessionStateTest {

    @Test
    fun defaultSessionHasFivePages() {
        val session = createDefaultProtoseqSessionState()

        assertEquals(DEFAULT_PROTOSEQ_PAGE_COUNT, session.pages.size)
    }

    @Test
    fun defaultSelectedPageIndexIsZero() {
        val session = createDefaultProtoseqSessionState()

        assertEquals(0, session.selectedPageIndex)
    }

    @Test
    fun defaultPageIndexesAreZeroThroughFour() {
        val session = createDefaultProtoseqSessionState()

        assertEquals(listOf(0, 1, 2, 3, 4), session.pages.map { it.pageIndex })
    }

    @Test
    fun defaultPagesAreEmptyAndDisabled() {
        val session = createDefaultProtoseqSessionState()

        assertTrue(session.pages.all { it.selectedSequencerType == SequencerType.EMPTY })
        assertTrue(session.pages.all { !it.enabled })
    }

    @Test
    fun updatingOnePagesTuringStateDoesNotModifyOtherPages() {
        val session = createDefaultProtoseqSessionState()
        val updated = session.updatePage(pageIndex = 2) { page ->
            page.copy(turingState = page.turingState.copy(sequenceLength = 32))
        }

        assertEquals(32, updated.pages[2].turingState.sequenceLength)
        assertEquals(
            StochasticSequencerUiState().sequenceLength,
            updated.pages[1].turingState.sequenceLength,
        )
        assertEquals(
            StochasticSequencerUiState().sequenceLength,
            updated.pages[3].turingState.sequenceLength,
        )
    }

    @Test
    fun selectingValidPageUpdatesSelectedPageIndex() {
        val session = createDefaultProtoseqSessionState()

        val updated = session.selectPage(4)

        assertEquals(4, updated.selectedPageIndex)
    }

    @Test
    fun selectingInvalidPageDoesNotCrashOrChangeSelection() {
        val session = createDefaultProtoseqSessionState()

        val updatedNegative = session.selectPage(-1)
        val updatedTooHigh = session.selectPage(10)

        assertEquals(session, updatedNegative)
        assertEquals(session, updatedTooHigh)
    }

    @Test
    fun updatingOnePageDoesNotModifyOtherPages() {
        val session = createDefaultProtoseqSessionState()

        val updated = session.updatePage(1) { page ->
            page.copy(enabled = true)
        }

        assertTrue(updated.pages[1].enabled)
        assertFalse(updated.pages[0].enabled)
        assertFalse(updated.pages[2].enabled)
        assertFalse(updated.pages[3].enabled)
        assertFalse(updated.pages[4].enabled)
    }

    @Test
    fun changingToTuringMachinePreservesExistingTuringState() {
        val customTuringState = StochasticSequencerUiState(sequenceLength = 24, baseNote = 60)
        val page = SequencerPageState(
            pageIndex = 0,
            selectedSequencerType = SequencerType.EMPTY,
            turingState = customTuringState,
        )

        val updated = page.withSequencerType(SequencerType.TURING_MACHINE)

        assertEquals(SequencerType.TURING_MACHINE, updated.selectedSequencerType)
        assertEquals(customTuringState, updated.turingState)
    }

    @Test
    fun changingBackToEmptyDoesNotEraseTuringState() {
        val customTuringState = StochasticSequencerUiState(sequenceLength = 12, baseNote = 72)
        val page = SequencerPageState(
            pageIndex = 1,
            selectedSequencerType = SequencerType.TURING_MACHINE,
            turingState = customTuringState,
        )

        val updated = page.withSequencerType(SequencerType.EMPTY)

        assertEquals(SequencerType.EMPTY, updated.selectedSequencerType)
        assertEquals(customTuringState, updated.turingState)
    }
}
