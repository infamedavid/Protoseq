package com.infamedavid.protoseq.features.session

import android.content.Context
import com.infamedavid.protoseq.features.grid616.Grid616SequencerUiState
import com.infamedavid.protoseq.features.grid616.Grid616StepState
import com.infamedavid.protoseq.features.grid616.Grid616TrackState
import com.infamedavid.protoseq.features.grid616.Grid616PlaybackMode
import com.infamedavid.protoseq.features.grid616.Grid616CrptSnapshot
import com.infamedavid.protoseq.features.grid616.Grid616CrptState
import com.infamedavid.protoseq.features.grid616.Grid616CrptStepSnapshot
import com.infamedavid.protoseq.features.grid616.Grid616CrptTrackSnapshot
import com.infamedavid.protoseq.features.grid616.normalized
import com.infamedavid.protoseq.features.sequencer.DEFAULT_PROTOSEQ_PAGE_COUNT
import com.infamedavid.protoseq.features.sequencer.PROTOSEQ_SESSION_STATE_VERSION
import com.infamedavid.protoseq.features.sequencer.ProtoseqSessionState
import com.infamedavid.protoseq.features.sequencer.SequencerPageState
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerUiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val LEGACY_FILE_NAME = "protoseq_session_state.json"
private const val PRESETS_FILE_NAME = "protoseq_session_presets.json"

data class ProtoseqSessionPreset(
    val id: String,
    val name: String,
    val updatedAtMillis: Long,
    val sessionState: ProtoseqSessionState,
)

data class ProtoseqSessionPresetSummary(
    val id: String,
    val name: String,
    val updatedAtMillis: Long,
)

class ProtoseqSessionStore(
    private val context: Context,
) {
    fun savePreset(name: String, sessionState: ProtoseqSessionState): Result<Unit> = runCatching {
        val presets = upsertSessionPreset(
            presets = readPresetCollectionWithLegacyMigration(),
            name = name,
            sessionState = sessionState,
            nowMillis = System.currentTimeMillis(),
            idGenerator = { UUID.randomUUID().toString() },
        )
        writePresetCollection(presets)
    }

    fun loadPreset(presetId: String): Result<ProtoseqSessionState> = runCatching {
        readPresetCollectionWithLegacyMigration()
            .firstOrNull { it.id == presetId }
            ?.sessionState
            ?: throw IllegalArgumentException("Preset not found")
    }

    fun listPresets(): Result<List<ProtoseqSessionPresetSummary>> = runCatching {
        readPresetCollectionWithLegacyMigration()
            .sortedByDescending { it.updatedAtMillis }
            .map { preset ->
                ProtoseqSessionPresetSummary(
                    id = preset.id,
                    name = preset.name,
                    updatedAtMillis = preset.updatedAtMillis,
                )
            }
    }

    fun hasPresets(): Boolean = listPresets().getOrNull()?.isNotEmpty() == true

    private fun readPresetCollectionWithLegacyMigration(): List<ProtoseqSessionPreset> {
        val presetsFile = File(context.filesDir, PRESETS_FILE_NAME)
        if (presetsFile.exists()) {
            return runCatching {
                protoseqSessionPresetsFromJsonObject(JSONObject(presetsFile.readText()))
            }.getOrElse { emptyList() }
        }

        val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
        if (!legacyFile.exists()) {
            return emptyList()
        }

        val migratedPresets = runCatching {
            listOf(
                ProtoseqSessionPreset(
                    id = UUID.randomUUID().toString(),
                    name = "Legacy State",
                    updatedAtMillis = legacyFile.lastModified(),
                    sessionState = protoseqSessionStateFromJsonObject(JSONObject(legacyFile.readText())),
                )
            )
        }.getOrElse { emptyList() }

        if (migratedPresets.isNotEmpty()) {
            runCatching { writePresetCollection(migratedPresets) }
        }

        return migratedPresets
    }

    private fun writePresetCollection(presets: List<ProtoseqSessionPreset>) {
        val file = File(context.filesDir, PRESETS_FILE_NAME)
        file.writeText(protoseqSessionPresetCollectionToJsonObject(presets).toString())
    }
}

fun upsertSessionPreset(
    presets: List<ProtoseqSessionPreset>,
    name: String,
    sessionState: ProtoseqSessionState,
    nowMillis: Long,
    idGenerator: () -> String,
): List<ProtoseqSessionPreset> {
    val trimmedName = name.trim()
    require(trimmedName.isNotBlank()) { "Preset name must not be blank" }

    val updated = presets.toMutableList()
    val existingIndex = updated.indexOfFirst { it.name.equals(trimmedName, ignoreCase = true) }
    if (existingIndex >= 0) {
        val existing = updated[existingIndex]
        updated[existingIndex] = existing.copy(
            name = trimmedName,
            updatedAtMillis = nowMillis,
            sessionState = sessionState,
        )
    } else {
        updated += ProtoseqSessionPreset(
            id = idGenerator(),
            name = trimmedName,
            updatedAtMillis = nowMillis,
            sessionState = sessionState,
        )
    }

    return updated
}

fun protoseqSessionPresetCollectionToJsonObject(presets: List<ProtoseqSessionPreset>): JSONObject {
    val presetsJson = JSONArray()
    presets.forEach { preset ->
        presetsJson.put(
            JSONObject()
                .put("id", preset.id)
                .put("name", preset.name)
                .put("updatedAtMillis", preset.updatedAtMillis)
                .put("session", preset.sessionState.toJsonObject())
        )
    }

    return JSONObject()
        .put("version", PROTOSEQ_SESSION_STATE_VERSION)
        .put("presets", presetsJson)
}

fun protoseqSessionPresetsFromJsonObject(json: JSONObject): List<ProtoseqSessionPreset> {
    val presetsJson = json.optJSONArray("presets") ?: JSONArray()
    val parsed = mutableListOf<ProtoseqSessionPreset>()

    for (i in 0 until presetsJson.length()) {
        val presetJson = presetsJson.optJSONObject(i) ?: continue
        val id = presetJson.optString("id", "").trim().ifBlank { UUID.randomUUID().toString() }
        val name = presetJson.optString("name", "").trim().ifBlank { "Preset ${i + 1}" }
        val updatedAtMillis = presetJson.optLong("updatedAtMillis", 0L)
        val sessionState = protoseqSessionStateFromJsonObject(
            presetJson.optJSONObject("session") ?: JSONObject()
        )

        parsed += ProtoseqSessionPreset(
            id = id,
            name = name,
            updatedAtMillis = updatedAtMillis,
            sessionState = sessionState,
        )
    }

    return parsed
}

fun ProtoseqSessionState.toJsonObject(): JSONObject {
    val pagesJson = JSONArray()
    pages.forEach { page ->
        pagesJson.put(
            JSONObject()
                .put("pageIndex", page.pageIndex)
                .put("enabled", page.enabled)
                .put("selectedSequencerType", page.selectedSequencerType.name)
                .put("turingState", page.turingState.toJsonObject())
                .put("grid616State", page.grid616State.toJsonObject())
        )
    }

    return JSONObject()
        .put("version", PROTOSEQ_SESSION_STATE_VERSION)
        .put("selectedPageIndex", selectedPageIndex)
        .put("pages", pagesJson)
}

fun protoseqSessionStateFromJsonObject(json: JSONObject): ProtoseqSessionState {
    val defaultSession = ProtoseqSessionState()
    val defaultPageByIndex = defaultSession.pages.associateBy { it.pageIndex }

    val parsedPages = mutableMapOf<Int, SequencerPageState>()
    val pagesJson = json.optJSONArray("pages") ?: JSONArray()
    for (i in 0 until pagesJson.length()) {
        val pageJson = pagesJson.optJSONObject(i) ?: continue
        val requestedIndex = pageJson.optInt("pageIndex", i)
        val normalizedPageIndex = requestedIndex.coerceIn(0, DEFAULT_PROTOSEQ_PAGE_COUNT - 1)
        val defaultPage = defaultPageByIndex[normalizedPageIndex] ?: SequencerPageState(pageIndex = normalizedPageIndex)

        val parsedPage = defaultPage.copy(
            pageIndex = normalizedPageIndex,
            enabled = pageJson.optBoolean("enabled", defaultPage.enabled),
            selectedSequencerType = enumValueOrDefault(
                rawName = pageJson.optString("selectedSequencerType", defaultPage.selectedSequencerType.name),
                default = defaultPage.selectedSequencerType,
            ),
            turingState = stochasticSequencerUiStateFromJsonObject(
                pageJson.optJSONObject("turingState"),
                defaultPage.turingState,
            ),
            grid616State = grid616SequencerUiStateFromJsonObject(
                pageJson.optJSONObject("grid616State"),
                defaultPage.grid616State,
            ),
        )

        parsedPages[normalizedPageIndex] = parsedPage
    }

    val pages = List(DEFAULT_PROTOSEQ_PAGE_COUNT) { pageIndex ->
        parsedPages[pageIndex] ?: defaultPageByIndex[pageIndex] ?: SequencerPageState(pageIndex = pageIndex)
    }

    val selectedPageIndex = json
        .optInt("selectedPageIndex", defaultSession.selectedPageIndex)
        .coerceIn(0, DEFAULT_PROTOSEQ_PAGE_COUNT - 1)

    return defaultSession.copy(
        selectedPageIndex = selectedPageIndex,
        pages = pages,
    )
}

private fun StochasticSequencerUiState.toJsonObject(): JSONObject = JSONObject()
    .put("lockPosition", lockPosition)
    .put("sequenceLength", sequenceLength)
    .put("slewAmount", slewAmount)
    .put("bernoulliProbability", bernoulliProbability)
    .put("pitchRangeSemitones", pitchRangeSemitones)
    .put("pitchOffset", pitchOffset)
    .put("gateLength", gateLength)
    .put("randomGateLength", randomGateLength)
    .put("outputMode", outputMode.name)
    .put("midiChannel", midiChannel)
    .put("baseNote", baseNote)
    .put("quantizationMode", quantizationMode.name)
    .put("ccNumber", ccNumber)
    .put("rptrBaseUnits", rptrBaseUnits)
    .put("rptrStartMode", rptrStartMode.name)

private fun stochasticSequencerUiStateFromJsonObject(
    json: JSONObject?,
    defaultState: StochasticSequencerUiState,
): StochasticSequencerUiState {
    if (json == null) return defaultState

    return defaultState.copy(
        lockPosition = json.optDouble("lockPosition", defaultState.lockPosition.toDouble()).toFloat(),
        sequenceLength = json.optInt("sequenceLength", defaultState.sequenceLength),
        slewAmount = json.optDouble("slewAmount", defaultState.slewAmount.toDouble()).toFloat(),
        bernoulliProbability = json
            .optDouble("bernoulliProbability", defaultState.bernoulliProbability.toDouble())
            .toFloat(),
        pitchRangeSemitones = json.optInt("pitchRangeSemitones", defaultState.pitchRangeSemitones),
        pitchOffset = json.optInt("pitchOffset", defaultState.pitchOffset),
        gateLength = json.optDouble("gateLength", defaultState.gateLength.toDouble()).toFloat(),
        randomGateLength = json
            .optDouble("randomGateLength", defaultState.randomGateLength.toDouble())
            .toFloat(),
        outputMode = enumValueOrDefault(
            rawName = json.optString("outputMode", defaultState.outputMode.name),
            default = defaultState.outputMode,
        ),
        midiChannel = json.optInt("midiChannel", defaultState.midiChannel),
        baseNote = json.optInt("baseNote", defaultState.baseNote),
        quantizationMode = enumValueOrDefault(
            rawName = json.optString("quantizationMode", defaultState.quantizationMode.name),
            default = defaultState.quantizationMode,
        ),
        ccNumber = json.optInt("ccNumber", defaultState.ccNumber),
        rptrBaseUnits = json.optInt("rptrBaseUnits", defaultState.rptrBaseUnits),
        rptrStartMode = enumValueOrDefault(
            rawName = json.optString("rptrStartMode", defaultState.rptrStartMode.name),
            default = defaultState.rptrStartMode,
        )
    )
}

private fun Grid616SequencerUiState.toJsonObject(): JSONObject = JSONObject()
    .put("midiChannel", midiChannel)
    .put("swingAmount", swingAmount)
    .put("crptState", crptState.toJsonObject())
    .put(
        "tracks",
        JSONArray().apply {
            tracks.forEach { track ->
                put(track.toJsonObject())
            }
        }
    )

private fun Grid616TrackState.toJsonObject(): JSONObject = JSONObject()
    .put("note", note)
    .put("muted", muted)
    .put("length", length)
    .put("playbackMode", playbackMode.name)
    .put(
        "steps",
        JSONArray().apply {
            steps.forEach { step ->
                put(step.toJsonObject())
            }
        }
    )

private fun Grid616StepState.toJsonObject(): JSONObject = JSONObject()
    .put("enabled", enabled)
    .put("velocity", velocity)
    .put("delayTicks", delayTicks)

private fun Grid616CrptState.toJsonObject(): JSONObject = JSONObject()
    .put("rndmAmount", rndmAmount)
    .put("snapshot", snapshot?.toJsonObject() ?: JSONObject.NULL)

private fun Grid616CrptSnapshot.toJsonObject(): JSONObject = JSONObject()
    .put(
        "tracks",
        JSONArray().apply {
            tracks.forEach { track ->
                put(track.toJsonObject())
            }
        }
    )

private fun Grid616CrptTrackSnapshot.toJsonObject(): JSONObject = JSONObject()
    .put("note", note)
    .put("length", length)
    .put("playbackMode", playbackMode.name)
    .put(
        "steps",
        JSONArray().apply {
            steps.forEach { step ->
                put(step.toJsonObject())
            }
        }
    )

private fun Grid616CrptStepSnapshot.toJsonObject(): JSONObject = JSONObject()
    .put("enabled", enabled)
    .put("velocity", velocity)
    .put("delayTicks", delayTicks)

private fun grid616SequencerUiStateFromJsonObject(
    json: JSONObject?,
    defaultState: Grid616SequencerUiState,
): Grid616SequencerUiState {
    if (json == null) return defaultState

    val legacyGlobalPlaybackMode = enumValueOrDefault(
        rawName = json.optString("playbackMode", Grid616PlaybackMode.FORWARD.name),
        default = Grid616PlaybackMode.FORWARD,
    )
    val defaultTrack = Grid616TrackState(note = 24)
    val tracks = (json.optJSONArray("tracks") ?: JSONArray()).let { tracksJson ->
        buildList {
            for (i in 0 until tracksJson.length()) {
                val trackJson = tracksJson.optJSONObject(i) ?: continue
                add(
                    grid616TrackStateFromJsonObject(
                        json = trackJson,
                        defaultState = defaultTrack,
                        playbackModeFallback = legacyGlobalPlaybackMode,
                    )
                )
            }
        }
    }

    return defaultState.copy(
        midiChannel = json.optInt("midiChannel", defaultState.midiChannel),
        swingAmount = json.optDouble("swingAmount", defaultState.swingAmount.toDouble()).toFloat(),
        tracks = tracks,
        crptState = grid616CrptStateFromJsonObject(json.optJSONObject("crptState"), defaultState.crptState),
    ).normalized()
}

private fun grid616TrackStateFromJsonObject(
    json: JSONObject,
    defaultState: Grid616TrackState,
    playbackModeFallback: Grid616PlaybackMode,
): Grid616TrackState {
    val steps = (json.optJSONArray("steps") ?: JSONArray()).let { stepsJson ->
        buildList {
            for (i in 0 until stepsJson.length()) {
                val stepJson = stepsJson.optJSONObject(i) ?: continue
                add(grid616StepStateFromJsonObject(stepJson, Grid616StepState()))
            }
        }
    }

    return defaultState.copy(
        note = json.optInt("note", defaultState.note),
        muted = json.optBoolean("muted", defaultState.muted),
        length = json.optInt("length", defaultState.length),
        playbackMode = enumValueOrDefault(
            rawName = json.optString("playbackMode", playbackModeFallback.name),
            default = playbackModeFallback,
        ),
        steps = steps,
    )
}

private fun grid616StepStateFromJsonObject(
    json: JSONObject,
    defaultState: Grid616StepState,
): Grid616StepState = defaultState.copy(
    enabled = json.optBoolean("enabled", defaultState.enabled),
    velocity = json.optInt("velocity", defaultState.velocity),
    delayTicks = json.optInt("delayTicks", defaultState.delayTicks),
)

private fun grid616CrptStateFromJsonObject(
    json: JSONObject?,
    defaultState: Grid616CrptState,
): Grid616CrptState {
    if (json == null) return defaultState

    val snapshot = when {
        !json.has("snapshot") -> defaultState.snapshot
        json.isNull("snapshot") -> null
        else -> grid616CrptSnapshotFromJsonObject(json.optJSONObject("snapshot"))
    }

    return defaultState.copy(
        rndmAmount = json.optDouble("rndmAmount", defaultState.rndmAmount.toDouble()).toFloat(),
        snapshot = snapshot,
    ).normalized()
}

private fun grid616CrptSnapshotFromJsonObject(json: JSONObject?): Grid616CrptSnapshot? {
    if (json == null) return null

    val tracks = (json.optJSONArray("tracks") ?: JSONArray()).let { tracksJson ->
        buildList {
            for (i in 0 until tracksJson.length()) {
                val trackJson = tracksJson.optJSONObject(i) ?: continue
                add(grid616CrptTrackSnapshotFromJsonObject(trackJson))
            }
        }
    }

    return Grid616CrptSnapshot(tracks = tracks).normalized()
}

private fun grid616CrptTrackSnapshotFromJsonObject(
    json: JSONObject,
): Grid616CrptTrackSnapshot {
    val steps = (json.optJSONArray("steps") ?: JSONArray()).let { stepsJson ->
        buildList {
            for (i in 0 until stepsJson.length()) {
                val stepJson = stepsJson.optJSONObject(i) ?: continue
                add(grid616CrptStepSnapshotFromJsonObject(stepJson))
            }
        }
    }

    return Grid616CrptTrackSnapshot(
        note = json.optInt("note", Grid616TrackState(note = 24).note),
        length = json.optInt("length", Grid616TrackState(note = 24).length),
        playbackMode = enumValueOrDefault(
            rawName = json.optString("playbackMode", Grid616PlaybackMode.FORWARD.name),
            default = Grid616PlaybackMode.FORWARD,
        ),
        steps = steps,
    ).normalized()
}

private fun grid616CrptStepSnapshotFromJsonObject(
    json: JSONObject,
): Grid616CrptStepSnapshot = Grid616CrptStepSnapshot(
    enabled = json.optBoolean("enabled", false),
    velocity = json.optInt("velocity", Grid616StepState().velocity),
    delayTicks = json.optInt("delayTicks", Grid616StepState().delayTicks),
).normalized()

private inline fun <reified T : Enum<T>> enumValueOrDefault(rawName: String?, default: T): T {
    if (rawName.isNullOrBlank()) return default
    return runCatching { enumValueOf<T>(rawName) }.getOrDefault(default)
}
