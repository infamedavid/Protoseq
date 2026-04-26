package com.infamedavid.protoseq.features.session

import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MAX_SEED
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MAX_SEQUENCE_LENGTH
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MIN_SEED
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MIN_SEQUENCE_LENGTH
import com.infamedavid.protoseq.features.ginaarp.GinaArpMode
import com.infamedavid.protoseq.features.ginaarp.GinaArpPlayMode
import com.infamedavid.protoseq.features.ginaarp.GinaArpSequencerUiState
import com.infamedavid.protoseq.features.ginaarp.GinaArpStepState
import com.infamedavid.protoseq.features.ginaarp.normalized
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_DELAY_TICKS
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_STEPS
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_TRACK_LENGTH
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_VELOCITY
import com.infamedavid.protoseq.features.grid616.GRID_616_MIN_DELAY_TICKS
import com.infamedavid.protoseq.features.grid616.GRID_616_MIN_TRACK_LENGTH
import com.infamedavid.protoseq.features.grid616.GRID_616_MIN_VELOCITY
import com.infamedavid.protoseq.features.grid616.GRID_616_TRACK_COUNT
import com.infamedavid.protoseq.features.grid616.GRID_616_CRPT_SLOT_COUNT
import com.infamedavid.protoseq.features.grid616.Grid616CrptState
import com.infamedavid.protoseq.features.grid616.Grid616CrptSlotState
import com.infamedavid.protoseq.features.grid616.Grid616PlaybackMode
import com.infamedavid.protoseq.features.grid616.Grid616SequencerUiState
import com.infamedavid.protoseq.features.grid616.Grid616StepState
import com.infamedavid.protoseq.features.grid616.Grid616TrackState
import com.infamedavid.protoseq.features.grid616.defaultGrid616Tracks
import com.infamedavid.protoseq.features.grid616.withSavedCrptSnapshot
import com.infamedavid.protoseq.features.sequencer.DEFAULT_PROTOSEQ_PAGE_COUNT
import com.infamedavid.protoseq.features.sequencer.ProtoseqSessionState
import com.infamedavid.protoseq.features.sequencer.SequencerType
import com.infamedavid.protoseq.features.sequencer.updatePage
import com.infamedavid.protoseq.features.stochastic.MidiOutputMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertTrue(serialized.contains("\"ginaArpState\""))

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
    fun ginaArpDefaultStateRoundTripPreservesNormalizedDefaults() {
        val session = ProtoseqSessionState()
        val decoded = protoseqSessionStateFromJsonObject(session.toJsonObject())
        val ginaArp = decoded.pages[0].ginaArpState

        assertEquals(GinaArpSequencerUiState().normalized(), ginaArp)
        assertEquals(8, ginaArp.sequenceLength)
        assertEquals(8, ginaArp.steps.size)
        assertEquals(1, ginaArp.seed)
        assertEquals(GinaArpMode.MAJOR, ginaArp.mode)
        assertEquals(GinaArpPlayMode.FORWARD, ginaArp.playMode)
    }

    @Test
    fun ginaArpCustomGlobalStateRoundTripPreservesValues() {
        val customState = GinaArpSequencerUiState(
            sequenceLength = 5,
            keyRootSemitone = 11,
            mode = GinaArpMode.DORIAN,
            playMode = GinaArpPlayMode.PING_PONG,
            seed = 42,
            globalRatioMultiplier = 0.75f,
            globalNoteOffset = -9,
            tempoDivisor = 6,
            gateLength = 0.3f,
            randomGateLength = 0.2f,
            bernoulliGate = 0.4f,
        ).normalized()
        val session = ProtoseqSessionState().updatePage(0) { page ->
            page.copy(ginaArpState = customState)
        }

        val decoded = protoseqSessionStateFromJsonObject(session.toJsonObject())
        val ginaArp = decoded.pages[0].ginaArpState

        assertEquals(customState, ginaArp)
    }

    @Test
    fun ginaArpCustomStepsRoundTripPreservesValues() {
        val customSteps = List(8) { GinaArpStepState() }.toMutableList().apply {
            this[0] = GinaArpStepState(
                enabled = true,
                degree = 6,
                octave = 5,
                ratio = 0.8f,
                divisions = 4,
                velocity = 92,
            )
            this[3] = GinaArpStepState(
                enabled = true,
                degree = 2,
                octave = 1,
                ratio = 0.15f,
                divisions = 7,
                velocity = 45,
            )
        }
        val customState = GinaArpSequencerUiState(steps = customSteps).normalized()
        val session = ProtoseqSessionState().updatePage(2) { page ->
            page.copy(ginaArpState = customState)
        }

        val decoded = protoseqSessionStateFromJsonObject(session.toJsonObject())
        val ginaArp = decoded.pages[2].ginaArpState

        assertEquals(customState.steps[0], ginaArp.steps[0])
        assertEquals(customState.steps[3], ginaArp.steps[3])
    }

    @Test
    fun missingGinaArpStateFallsBackToDefaults() {
        val json = JSONObject()
            .put("version", 1)
            .put("selectedPageIndex", 0)
            .put(
                "pages",
                JSONArray().put(
                    JSONObject()
                        .put("pageIndex", 0)
                        .put("enabled", true)
                        .put("selectedSequencerType", SequencerType.EMPTY.name)
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)

        assertEquals(GinaArpSequencerUiState().normalized(), decoded.pages[0].ginaArpState)
    }

    @Test
    fun invalidIncompleteGinaArpStateNormalizesSafely() {
        val tooFewSteps = JSONArray().put(
            JSONObject()
                .put("enabled", true)
                .put("degree", 99)
                .put("octave", -5)
                .put("ratio", 9.1)
                .put("divisions", 999)
                .put("velocity", 0)
        )
        val tooManySteps = JSONArray().apply {
            repeat(20) { index ->
                put(
                    JSONObject()
                        .put("enabled", index % 2 == 0)
                        .put("degree", if (index == 0) -1 else 4)
                        .put("octave", 99)
                        .put("ratio", -2.0)
                        .put("divisions", 0)
                        .put("velocity", 999)
                )
            }
        }
        val json = JSONObject()
            .put("version", 1)
            .put("selectedPageIndex", 0)
            .put(
                "pages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("pageIndex", 0)
                            .put(
                                "ginaArpState",
                                JSONObject()
                                    .put("sequenceLength", GINA_ARP_MAX_SEQUENCE_LENGTH + 10)
                                    .put("seed", GINA_ARP_MIN_SEED - 10)
                                    .put("mode", "NOT_A_MODE")
                                    .put("playMode", "NOT_A_PLAY_MODE")
                                    .put("steps", tooFewSteps)
                            )
                    )
                    .put(
                        JSONObject()
                            .put("pageIndex", 1)
                            .put(
                                "ginaArpState",
                                JSONObject()
                                    .put("sequenceLength", GINA_ARP_MIN_SEQUENCE_LENGTH - 5)
                                    .put("seed", GINA_ARP_MAX_SEED + 100)
                                    .put("mode", "WRONG")
                                    .put("playMode", "WRONG")
                                    .put("steps", tooManySteps)
                            )
                    )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)
        val page0State = decoded.pages[0].ginaArpState
        val page1State = decoded.pages[1].ginaArpState

        assertEquals(GINA_ARP_MAX_SEQUENCE_LENGTH, page0State.sequenceLength)
        assertEquals(GINA_ARP_MIN_SEED, page0State.seed)
        assertEquals(GinaArpMode.MAJOR, page0State.mode)
        assertEquals(GinaArpPlayMode.FORWARD, page0State.playMode)
        assertEquals(8, page0State.steps.size)
        assertEquals(1f, page0State.steps[0].ratio)
        assertEquals(7, page0State.steps[0].divisions)
        assertEquals(1, page0State.steps[0].velocity)

        assertEquals(GINA_ARP_MIN_SEQUENCE_LENGTH, page1State.sequenceLength)
        assertEquals(GINA_ARP_MAX_SEED, page1State.seed)
        assertEquals(GinaArpMode.MAJOR, page1State.mode)
        assertEquals(GinaArpPlayMode.FORWARD, page1State.playMode)
        assertEquals(8, page1State.steps.size)
        assertEquals(0f, page1State.steps[0].ratio)
        assertEquals(1, page1State.steps[0].divisions)
        assertEquals(127, page1State.steps[0].velocity)
    }

    @Test
    fun grid616StateRoundTripPreservesEditableState() {
        val input = ProtoseqSessionState().updatePage(0) { page ->
            page.copy(
                selectedSequencerType = SequencerType.GRID_616,
                grid616State = page.grid616State.copy(
                    midiChannel = 16,
                    swingAmount = 0.5f,
                    tracks = page.grid616State.tracks.toMutableList().apply {
                        this[0] = this[0].copy(
                            note = 51,
                            muted = true,
                            length = 11,
                            playbackMode = Grid616PlaybackMode.REVERSE,
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
        assertEquals(Grid616PlaybackMode.REVERSE, decodedGrid616.tracks[0].playbackMode)
        assertTrue(decodedGrid616.tracks.drop(1).all { it.playbackMode == Grid616PlaybackMode.FORWARD })
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
    fun invalidTrackGrid616PlaybackModeFallsBackToDefault() {
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
                                .put(
                                    "tracks",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("note", 40)
                                            .put("playbackMode", "NOPE")
                                    )
                                )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)

        assertEquals(Grid616PlaybackMode.FORWARD, decoded.pages[0].grid616State.tracks[0].playbackMode)
    }

    @Test
    fun missingTrackPlaybackModeFallsBackToLegacyGlobalPlaybackMode() {
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
                                .put("playbackMode", Grid616PlaybackMode.PING_PONG.name)
                                .put(
                                    "tracks",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("note", 36)
                                            .put("length", 8)
                                    )
                                )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)

        assertEquals(Grid616PlaybackMode.PING_PONG, decoded.pages[0].grid616State.tracks[0].playbackMode)
    }

    @Test
    fun missingTrackPlaybackModeFallsBackToForwardWhenNoLegacyModeExists() {
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
                                .put(
                                    "tracks",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("note", 36)
                                            .put("length", 8)
                                    )
                                )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)

        assertEquals(Grid616PlaybackMode.FORWARD, decoded.pages[0].grid616State.tracks[0].playbackMode)
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

    @Test
    fun grid616StateParsingClampsLegacyDelayValueToCurrentMax() {
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
                                .put(
                                    "tracks",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("note", 36)
                                            .put(
                                                "steps",
                                                JSONArray().put(
                                                    JSONObject()
                                                        .put("enabled", true)
                                                        .put("velocity", 100)
                                                        .put("delayTicks", 23)
                                                )
                                            )
                                    )
                                )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)
        val step = decoded.pages[0].grid616State.tracks.first().steps.first()

        assertEquals(GRID_616_MAX_DELAY_TICKS, step.delayTicks)
        assertEquals(16, step.delayTicks)
    }

    @Test
    fun grid616CrptEmptyStateRoundTripPreservesDefaults() {
        val session = ProtoseqSessionState()
            .updatePage(0) { page ->
                page.copy(
                    selectedSequencerType = SequencerType.GRID_616,
                    grid616State = page.grid616State.copy(crptState = Grid616CrptState()),
                )
            }

        val decoded = protoseqSessionStateFromJsonObject(session.toJsonObject())
        val crpt = decoded.pages[0].grid616State.crptState

        assertEquals(0f, crpt.rndmAmount)
        assertTrue(crpt.slots.all { it.snapshot == null })
    }

    @Test
    fun grid616CrptSnapshotRoundTripPreservesSnapshotContent() {
        val session = ProtoseqSessionState().updatePage(0) { page ->
            val patternGrid = page.grid616State.copy(
                tracks = page.grid616State.tracks.toMutableList().apply {
                    this[0] = this[0].copy(
                        note = 62,
                        length = 7,
                        playbackMode = Grid616PlaybackMode.RANDOM,
                        steps = this[0].steps.toMutableList().apply {
                            this[0] = Grid616StepState(
                                enabled = true,
                                velocity = 99,
                                delayTicks = 12,
                            )
                        },
                    )
                }
            )
            val savedGrid = patternGrid.withSavedCrptSnapshot(0)
            val modifiedGrid = savedGrid.copy(
                crptState = savedGrid.crptState.copy(rndmAmount = 0.5f),
            )

            page.copy(
                selectedSequencerType = SequencerType.GRID_616,
                grid616State = modifiedGrid,
            )
        }

        val decoded = protoseqSessionStateFromJsonObject(session.toJsonObject())
        val crpt = decoded.pages[0].grid616State.crptState
        val snapshot = crpt.slots[0].snapshot

        assertEquals(0.5f, crpt.rndmAmount)
        assertNotNull(snapshot)
        assertEquals(62, snapshot!!.tracks[0].note)
        assertEquals(7, snapshot.tracks[0].length)
        assertEquals(Grid616PlaybackMode.RANDOM, snapshot.tracks[0].playbackMode)
        assertTrue(snapshot.tracks[0].steps[0].enabled)
        assertEquals(99, snapshot.tracks[0].steps[0].velocity)
        assertEquals(12, snapshot.tracks[0].steps[0].delayTicks)
    }

    @Test
    fun grid616CrptSlotsRoundTripPreservesSlotZeroAndFiveAndRndmAmount() {
        val source = Grid616SequencerUiState(
            tracks = defaultGrid616Tracks().map { it.copy(note = 61) }
        )
        val sourceTwo = Grid616SequencerUiState(
            tracks = defaultGrid616Tracks().map { it.copy(note = 77) }
        )
        val crptWithTwoSlots = Grid616CrptState(
            rndmAmount = 0.35f,
            slots = List(GRID_616_CRPT_SLOT_COUNT) { index ->
                when (index) {
                    0 -> Grid616CrptSlotState(snapshot = source.toCrptSnapshot())
                    5 -> Grid616CrptSlotState(snapshot = sourceTwo.toCrptSnapshot())
                    else -> Grid616CrptSlotState()
                }
            }
        )

        val session = ProtoseqSessionState().updatePage(0) { page ->
            page.copy(
                selectedSequencerType = SequencerType.GRID_616,
                grid616State = page.grid616State.copy(crptState = crptWithTwoSlots),
            )
        }

        val decoded = protoseqSessionStateFromJsonObject(session.toJsonObject())
        val crpt = decoded.pages[0].grid616State.crptState

        assertEquals(GRID_616_CRPT_SLOT_COUNT, crpt.slots.size)
        assertEquals(0.35f, crpt.rndmAmount)
        assertEquals(61, crpt.slots[0].snapshot!!.tracks[0].note)
        assertEquals(77, crpt.slots[5].snapshot!!.tracks[0].note)
    }

    @Test
    fun grid616CrptSnapshotExcludesMuteWhileGridTrackMuteStillRoundTrips() {
        val session = ProtoseqSessionState().updatePage(0) { page ->
            val mutatedGrid = page.grid616State.copy(
                tracks = page.grid616State.tracks.toMutableList().apply {
                    this[0] = this[0].copy(muted = true)
                },
            ).withSavedCrptSnapshot(0)

            page.copy(
                selectedSequencerType = SequencerType.GRID_616,
                grid616State = mutatedGrid,
            )
        }

        val decoded = protoseqSessionStateFromJsonObject(session.toJsonObject())
        val grid = decoded.pages[0].grid616State

        assertTrue(grid.tracks[0].muted)
        assertNotNull(grid.crptState.slots[0].snapshot)
    }

    @Test
    fun missingGrid616CrptStateFallsBackToDefaults() {
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
                                .put(
                                    "tracks",
                                    JSONArray().put(JSONObject().put("note", 40))
                                )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)

        assertEquals(Grid616CrptState(), decoded.pages[0].grid616State.crptState)
        assertTrue(decoded.pages[0].grid616State.crptState.slots.all { it.snapshot == null })
    }

    @Test
    fun legacyGrid616CrptSnapshotMigratesIntoSlotZero() {
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
                                .put(
                                    "crptState",
                                    JSONObject()
                                        .put("rndmAmount", 0.5)
                                        .put(
                                            "snapshot",
                                            JSONObject().put(
                                                "tracks",
                                                JSONArray().put(
                                                    JSONObject()
                                                        .put("note", 72)
                                                        .put("length", 8)
                                                        .put("playbackMode", Grid616PlaybackMode.FORWARD.name)
                                                        .put("steps", JSONArray())
                                                )
                                            )
                                        )
                                )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)
        val crpt = decoded.pages[0].grid616State.crptState

        assertEquals(0.5f, crpt.rndmAmount)
        assertEquals(72, crpt.slots[0].snapshot!!.tracks[0].note)
        assertTrue(crpt.slots.drop(1).all { it.snapshot == null })
    }

    @Test
    fun nullGrid616CrptSnapshotLoadsAsNull() {
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
                                .put(
                                    "crptState",
                                    JSONObject()
                                        .put("rndmAmount", 0.8)
                                        .put("snapshot", JSONObject.NULL)
                                )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)
        val crpt = decoded.pages[0].grid616State.crptState

        assertEquals(0.8f, crpt.rndmAmount)
        assertTrue(crpt.slots.all { it.snapshot == null })
    }

    @Test
    fun invalidGrid616CrptValuesNormalizeSafely() {
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
                                .put(
                                    "crptState",
                                    JSONObject()
                                        .put("rndmAmount", 9.0)
                                        .put(
                                            "snapshot",
                                            JSONObject().put(
                                                "tracks",
                                                JSONArray().put(
                                                    JSONObject()
                                                        .put("note", 999)
                                                        .put("length", 0)
                                                        .put("playbackMode", "INVALID")
                                                        .put(
                                                            "steps",
                                                            JSONArray().put(
                                                                JSONObject()
                                                                    .put("enabled", true)
                                                                    .put("velocity", 999)
                                                                    .put("delayTicks", 999)
                                                            )
                                                        )
                                                )
                                            )
                                        )
                                )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)
        val snapshot = decoded.pages[0].grid616State.crptState.slots[0].snapshot!!
        val track = snapshot.tracks[0]
        val step = track.steps[0]

        assertEquals(1f, decoded.pages[0].grid616State.crptState.rndmAmount)
        assertEquals(127, track.note)
        assertEquals(GRID_616_MIN_TRACK_LENGTH, track.length)
        assertEquals(Grid616PlaybackMode.FORWARD, track.playbackMode)
        assertEquals(GRID_616_MAX_VELOCITY, step.velocity)
        assertEquals(GRID_616_MAX_DELAY_TICKS, step.delayTicks)
    }

    @Test
    fun invalidGrid616CrptRndmAmountBelowZeroClampsToZero() {
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
                                .put(
                                    "crptState",
                                    JSONObject().put("rndmAmount", -0.1)
                                )
                        )
                )
            )

        val decoded = protoseqSessionStateFromJsonObject(json)
        assertEquals(0f, decoded.pages[0].grid616State.crptState.rndmAmount)
    }

    @Test
    fun incompleteGrid616CrptSnapshotNormalizesTrackAndStepCounts() {
        val jsonWithTooFew = JSONObject()
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
                                .put(
                                    "crptState",
                                    JSONObject()
                                        .put(
                                            "snapshot",
                                            JSONObject().put(
                                                "tracks",
                                                JSONArray().put(
                                                    JSONObject()
                                                        .put("note", 48)
                                                        .put("length", 16)
                                                        .put("playbackMode", Grid616PlaybackMode.FORWARD.name)
                                                        .put(
                                                            "steps",
                                                            JSONArray().put(
                                                                JSONObject()
                                                                    .put("enabled", true)
                                                                    .put("velocity", 90)
                                                                    .put("delayTicks", 2)
                                                            )
                                                        )
                                                )
                                            )
                                        )
                                )
                        )
                )
            )

        val jsonWithTooMany = JSONObject()
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
                                .put(
                                    "crptState",
                                    JSONObject()
                                        .put(
                                            "snapshot",
                                            JSONObject().put(
                                                "tracks",
                                                JSONArray().apply {
                                                    repeat(GRID_616_TRACK_COUNT + 2) {
                                                        put(
                                                            JSONObject()
                                                                .put("note", 48 + it)
                                                                .put("length", 8)
                                                                .put("playbackMode", Grid616PlaybackMode.FORWARD.name)
                                                                .put(
                                                                    "steps",
                                                                    JSONArray().apply {
                                                                        repeat(GRID_616_MAX_STEPS + 3) { stepIndex ->
                                                                            put(
                                                                                JSONObject()
                                                                                    .put("enabled", stepIndex % 2 == 0)
                                                                                    .put("velocity", 80)
                                                                                    .put("delayTicks", 1)
                                                                            )
                                                                        }
                                                                    }
                                                                )
                                                        )
                                                    }
                                                }
                                            )
                                        )
                                )
                        )
                )
            )

        val decodedFew = protoseqSessionStateFromJsonObject(jsonWithTooFew)
        val decodedMany = protoseqSessionStateFromJsonObject(jsonWithTooMany)

        val fewSnapshot = decodedFew.pages[0].grid616State.crptState.slots[0].snapshot!!
        val manySnapshot = decodedMany.pages[0].grid616State.crptState.slots[0].snapshot!!

        assertEquals(GRID_616_TRACK_COUNT, fewSnapshot.tracks.size)
        assertTrue(fewSnapshot.tracks.all { it.steps.size == GRID_616_MAX_STEPS })
        assertEquals(GRID_616_TRACK_COUNT, manySnapshot.tracks.size)
        assertTrue(manySnapshot.tracks.all { it.steps.size == GRID_616_MAX_STEPS })
    }
}
