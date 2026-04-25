package com.infamedavid.protoseq.features.session

import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_DELAY_TICKS
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_STEPS
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_TRACK_LENGTH
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_VELOCITY
import com.infamedavid.protoseq.features.grid616.GRID_616_MIN_DELAY_TICKS
import com.infamedavid.protoseq.features.grid616.GRID_616_MIN_TRACK_LENGTH
import com.infamedavid.protoseq.features.grid616.GRID_616_MIN_VELOCITY
import com.infamedavid.protoseq.features.grid616.GRID_616_TRACK_COUNT
import com.infamedavid.protoseq.features.grid616.Grid616PlaybackMode
import com.infamedavid.protoseq.features.grid616.Grid616SequencerUiState
import com.infamedavid.protoseq.features.grid616.Grid616StepState
import com.infamedavid.protoseq.features.grid616.Grid616TrackState
import com.infamedavid.protoseq.features.sequencer.DEFAULT_PROTOSEQ_PAGE_COUNT
import com.infamedavid.protoseq.features.sequencer.ProtoseqSessionState
import com.infamedavid.protoseq.features.sequencer.SequencerType
import com.infamedavid.protoseq.features.sequencer.updatePage
import com.infamedavid.protoseq.features.stochastic.MidiOutputMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun sessionJsonContainsEditableStateAndExcludesRuntimeState() {
        val session = ProtoseqSessionState(selectedPageIndex = 3)
            .updatePage(0) { page ->
                page.copy(
                    enabled = true,
                    selectedSequencerType = SequencerType.TURING_MACHINE,
                )
            }

        val json = session.toJsonObject()
        val serialized = json.toString()

        assertTrue(json.has("version"))
        assertTrue(json.has("selectedPageIndex"))
        assertTrue(json.has("pages"))

        assertTrue(serialized.contains("\"enabled\""))
        assertTrue(serialized.contains("\"selectedSequencerType\""))
        assertTrue(serialized.contains("\"turingState\""))
        assertTrue(serialized.contains("\"grid616State\""))

        val forbiddenRuntimeKeys = listOf(
            "pageRuntimes",
            "scheduledNoteOffs",
            "activeCcSlew",
            "rptrStatesByPage",
            "activeRptrDivisionsByPage",
            "playing",
            "selectedMidiOutputId",
            "clockPosition",
            "activeMidiNotes",
        )

        forbiddenRuntimeKeys.forEach { key ->
            assertFalse("Unexpected runtime key found in session JSON: $key", serialized.contains("\"$key\""))
        }
    }

    @Test
    fun grid616StateRoundTripPreservesEditableState() {
        val input = ProtoseqSessionState().updatePage(0) { page ->
            page.copy(
                selectedSequencerType = SequencerType.GRID_616,
                grid616State = page.grid616State.copy(
                    midiChannel = 16,
                    swingAmount = 0.5f,
                    playbackMode = Grid616PlaybackMode.REVERSE,
                    tracks = page.grid616State.tracks.toMutableList().apply {
                        this[0] = this[0].copy(
                            note = 51,
                            muted = true,
                            length = 11,
                            steps = this[0].steps.toMutableList().apply {
                                this[0] = Grid616StepState(
                                    enabled = true,
                                    velocity = 87,
                                    delayTicks = 9,
                                )
                            }
                        )
                    }
                )
            )
        }

        val decoded = protoseqSessionStateFromJsonObject(input.toJsonObject())
        val decodedGrid616 = decoded.pages[0].grid616State

        assertEquals(16, decodedGrid616.midiChannel)
        assertEquals(0.5f, decodedGrid616.swingAmount)
        assertEquals(Grid616PlaybackMode.REVERSE, decodedGrid616.playbackMode)
        assertEquals(51, decodedGrid616.tracks[0].note)
        assertTrue(decodedGrid616.tracks[0].muted)
        assertEquals(11, decodedGrid616.tracks[0].length)
        assertTrue(decodedGrid616.tracks[0].steps[0].enabled)
        assertEquals(87, decodedGrid616.tracks[0].steps[0].velocity)
        assertEquals(9, decodedGrid616.tracks[0].steps[0].delayTicks)
    }

    @Test
    fun missingGrid616StateFallsBackToDefaults() {
        val json = JSONObject()
            .put("version", 1)
            .put("selectedPageIndex", 0)
            .put(
                "pages",
                JSONArray().put(
                    JSONObject()
                        .put("pageIndex", 0)
                        .put("enabled", true)
                        .put("selectedSequencerType", SequencerType.GRID_616.name)
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)

        assertEquals(Grid616SequencerUiState(), decoded.pages[0].grid616State)
    }

    @Test
    fun invalidGrid616PlaybackModeFallsBackToDefault() {
        val json = JSONObject()
            .put("version", 1)
            .put("selectedPageIndex", 0)
            .put(
                "pages",
                JSONArray().put(
                    JSONObject()
                        .put("pageIndex", 0)
                        .put("selectedSequencerType", SequencerType.GRID_616.name)
                        .put(
                            "grid616State",
                            JSONObject()
                                .put("playbackMode", "NOPE")
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)

        assertEquals(Grid616PlaybackMode.FORWARD, decoded.pages[0].grid616State.playbackMode)
    }

    @Test
    fun malformedGrid616TrackAndStepSizesNormalizeToExpectedCounts() {
        val json = JSONObject()
            .put("version", 1)
            .put("selectedPageIndex", 0)
            .put(
                "pages",
                JSONArray().put(
                    JSONObject()
                        .put("pageIndex", 0)
                        .put("selectedSequencerType", SequencerType.GRID_616.name)
                        .put(
                            "grid616State",
                            JSONObject().put(
                                "tracks",
                                JSONArray().put(
                                    JSONObject()
                                        .put("note", 40)
                                        .put("length", 8)
                                        .put(
                                            "steps",
                                            JSONArray()
                                                .put(JSONObject().put("enabled", true))
                                        )
                                )
                            )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)
        val state = decoded.pages[0].grid616State

        assertEquals(GRID_616_TRACK_COUNT, state.tracks.size)
        assertTrue(state.tracks.all { it.steps.size == GRID_616_MAX_STEPS })
    }

    @Test
    fun grid616StateParsingClampsOutOfRangeValues() {
        val json = JSONObject()
            .put("version", 1)
            .put("selectedPageIndex", 0)
            .put(
                "pages",
                JSONArray().put(
                    JSONObject()
                        .put("pageIndex", 0)
                        .put("selectedSequencerType", SequencerType.GRID_616.name)
                        .put(
                            "grid616State",
                            JSONObject()
                                .put("midiChannel", 99)
                                .put("swingAmount", 9.2)
                                .put(
                                    "tracks",
                                    JSONArray().put(
                                        Grid616TrackState(
                                            note = 30,
                                            length = 0,
                                            steps = listOf(
                                                Grid616StepState(velocity = 0, delayTicks = -1),
                                                Grid616StepState(velocity = 999, delayTicks = 99),
                                            )
                                        ).let { invalidTrack ->
                                            JSONObject()
                                                .put("note", invalidTrack.note)
                                                .put("muted", invalidTrack.muted)
                                                .put("length", invalidTrack.length)
                                                .put(
                                                    "steps",
                                                    JSONArray()
                                                        .put(
                                                            JSONObject()
                                                                .put("enabled", invalidTrack.steps[0].enabled)
                                                                .put("velocity", invalidTrack.steps[0].velocity)
                                                                .put("delayTicks", invalidTrack.steps[0].delayTicks)
                                                        )
                                                        .put(
                                                            JSONObject()
                                                                .put("enabled", invalidTrack.steps[1].enabled)
                                                                .put("velocity", invalidTrack.steps[1].velocity)
                                                                .put("delayTicks", invalidTrack.steps[1].delayTicks)
                                                        )
                                                )
                                        }
                                    )
                                )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)
        val state = decoded.pages[0].grid616State
        val track = state.tracks.first()

        assertEquals(16, state.midiChannel)
        assertEquals(0.75f, state.swingAmount)
        assertEquals(GRID_616_MIN_TRACK_LENGTH, track.length)
        assertEquals(GRID_616_MIN_VELOCITY, track.steps[0].velocity)
        assertEquals(GRID_616_MIN_DELAY_TICKS, track.steps[0].delayTicks)
        assertEquals(GRID_616_MAX_VELOCITY, track.steps[1].velocity)
        assertEquals(GRID_616_MAX_DELAY_TICKS, track.steps[1].delayTicks)
        assertTrue(track.length in GRID_616_MIN_TRACK_LENGTH..GRID_616_MAX_TRACK_LENGTH)
    }
}
