package com.infamedavid.protoseq.features.session

import android.content.Context
import com.infamedavid.protoseq.features.sequencer.DEFAULT_PROTOSEQ_PAGE_COUNT
import com.infamedavid.protoseq.features.sequencer.PROTOSEQ_SESSION_STATE_VERSION
import com.infamedavid.protoseq.features.sequencer.ProtoseqSessionState
import com.infamedavid.protoseq.features.sequencer.SequencerPageState
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerUiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val FILE_NAME = "protoseq_session_state.json"

class ProtoseqSessionStore(
    private val context: Context,
) {
    fun save(sessionState: ProtoseqSessionState): Result<Unit> = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(sessionState.toJsonObject().toString())
    }

    fun load(): Result<ProtoseqSessionState> = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        val contents = file.readText()
        protoseqSessionStateFromJsonObject(JSONObject(contents))
    }

    fun hasSavedState(): Boolean = File(context.filesDir, FILE_NAME).exists()
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
            )
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

private inline fun <reified T : Enum<T>> enumValueOrDefault(rawName: String?, default: T): T {
    if (rawName.isNullOrBlank()) return default
    return runCatching { enumValueOf<T>(rawName) }.getOrDefault(default)
}
