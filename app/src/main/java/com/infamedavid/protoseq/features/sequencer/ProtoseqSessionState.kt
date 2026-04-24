package com.infamedavid.protoseq.features.sequencer

const val DEFAULT_PROTOSEQ_PAGE_COUNT = 5
const val PROTOSEQ_SESSION_STATE_VERSION = 1

data class ProtoseqSessionState(
    val version: Int = PROTOSEQ_SESSION_STATE_VERSION,
    val selectedPageIndex: Int = 0,
    val pages: List<SequencerPageState> = List(DEFAULT_PROTOSEQ_PAGE_COUNT) { pageIndex ->
        SequencerPageState(pageIndex = pageIndex)
    },
)

fun createDefaultProtoseqSessionState(): ProtoseqSessionState = ProtoseqSessionState()
