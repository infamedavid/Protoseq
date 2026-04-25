package com.infamedavid.protoseq.features.session

import com.infamedavid.protoseq.features.sequencer.DEFAULT_PROTOSEQ_PAGE_COUNT
import com.infamedavid.protoseq.features.sequencer.ProtoseqSessionState
import com.infamedavid.protoseq.features.sequencer.SequencerType
import com.infamedavid.protoseq.features.sequencer.updatePage
import com.infamedavid.protoseq.features.stochastic.MidiOutputMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtoseqSessionStoreTest {

    @Test
    fun sessionRoundTripPreservesEditableState() {
        val input = ProtoseqSessionState(selectedPageIndex = 2)
            .updatePage(1) { page ->
                page.copy(
                    enabled = true,
                    selectedSequencerType = SequencerType.TURING_MACHINE,
                    turingState = page.turingState.copy(
                        lockPosition = 0.4f,
                        sequenceLength = 24,
                        slewAmount = 0.33f,
                        bernoulliProbability = 0.5f,
                        pitchRangeSemitones = 31,
                        pitchOffset = -7,
                        gateLength = 0.8f,
                        randomGateLength = 0.41f,
                        outputMode = MidiOutputMode.CC,
                        midiChannel = 13,
                        baseNote = 67,
                        ccNumber = 42,
                        rptrBaseUnits = 6,
                    )
                )
            }

        val decoded = protoseqSessionStateFromJsonObject(input.toJsonObject())

        assertEquals(2, decoded.selectedPageIndex)
        assertTrue(decoded.pages[1].enabled)
        assertEquals(SequencerType.TURING_MACHINE, decoded.pages[1].selectedSequencerType)
        assertEquals(input.pages[1].turingState, decoded.pages[1].turingState)
    }

    @Test
    fun presetCollectionRoundTripPreservesEntries() {
        val presets = listOf(
            ProtoseqSessionPreset(
                id = "preset-1",
                name = "Test 1",
                updatedAtMillis = 100L,
                sessionState = ProtoseqSessionState(selectedPageIndex = 1)
            ),
            ProtoseqSessionPreset(
                id = "preset-2",
                name = "Test 2",
                updatedAtMillis = 200L,
                sessionState = ProtoseqSessionState(selectedPageIndex = 3)
            )
        )

        val decoded = protoseqSessionPresetsFromJsonObject(
            protoseqSessionPresetCollectionToJsonObject(presets)
        )

        assertEquals(2, decoded.size)
        assertEquals("preset-1", decoded[0].id)
        assertEquals("Test 2", decoded[1].name)
        assertEquals(3, decoded[1].sessionState.selectedPageIndex)
    }

    @Test
    fun savingSameNameOverwritesExistingPresetId() {
        val original = listOf(
            ProtoseqSessionPreset(
                id = "same-id",
                name = "Test 1",
                updatedAtMillis = 100L,
                sessionState = ProtoseqSessionState(selectedPageIndex = 0)
            )
        )

        val updated = upsertSessionPreset(
            presets = original,
            name = "  test 1 ",
            sessionState = ProtoseqSessionState(selectedPageIndex = 4),
            nowMillis = 999L,
            idGenerator = { "new-id" }
        )

        assertEquals(1, updated.size)
        assertEquals("same-id", updated[0].id)
        assertEquals("test 1", updated[0].name)
        assertEquals(999L, updated[0].updatedAtMillis)
        assertEquals(4, updated[0].sessionState.selectedPageIndex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankPresetNameIsRejected() {
        upsertSessionPreset(
            presets = emptyList(),
            name = "   ",
            sessionState = ProtoseqSessionState(),
            nowMillis = 123L,
            idGenerator = { "id-1" }
        )
    }

    @Test
    fun invalidEnumsFallBackToDefaults() {
        val json = JSONObject()
            .put("version", 1)
            .put("selectedPageIndex", 0)
            .put(
                "pages",
                JSONArray().put(
                    JSONObject()
                        .put("pageIndex", 0)
                        .put("enabled", true)
                        .put("selectedSequencerType", "NOPE")
                        .put(
                            "turingState",
                            JSONObject()
                                .put("outputMode", "BAD")
                                .put("quantizationMode", "BAD")
                                .put("rptrStartMode", "BAD")
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)
        val defaults = ProtoseqSessionState().pages[0]

        assertEquals(defaults.selectedSequencerType, decoded.pages[0].selectedSequencerType)
        assertEquals(defaults.turingState.outputMode, decoded.pages[0].turingState.outputMode)
        assertEquals(defaults.turingState.quantizationMode, decoded.pages[0].turingState.quantizationMode)
        assertEquals(defaults.turingState.rptrStartMode, decoded.pages[0].turingState.rptrStartMode)
    }

    @Test
    fun loadingWithFewerPagesPadsToFivePages() {
        val json = JSONObject()
            .put("version", 1)
            .put("selectedPageIndex", 4)
            .put(
                "pages",
                JSONArray().put(
                    JSONObject()
                        .put("pageIndex", 0)
                        .put("enabled", true)
                        .put("selectedSequencerType", SequencerType.TURING_MACHINE.name)
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)

        assertEquals(DEFAULT_PROTOSEQ_PAGE_COUNT, decoded.pages.size)
        assertEquals(4, decoded.selectedPageIndex)
        assertTrue(decoded.pages[0].enabled)
    }
}
