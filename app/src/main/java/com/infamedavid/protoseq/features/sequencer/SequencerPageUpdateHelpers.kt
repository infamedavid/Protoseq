package com.infamedavid.protoseq.features.sequencer

fun ProtoseqSessionState.selectPage(index: Int): ProtoseqSessionState {
    if (index !in pages.indices) return this
    return copy(selectedPageIndex = index)
}

fun ProtoseqSessionState.updatePage(
    pageIndex: Int,
    transform: (SequencerPageState) -> SequencerPageState,
): ProtoseqSessionState {
    val existingIndex = pages.indexOfFirst { it.pageIndex == pageIndex }
    if (existingIndex == -1) return this

    val updatedPages = pages.toMutableList()
    updatedPages[existingIndex] = transform(updatedPages[existingIndex])
    return copy(pages = updatedPages)
}

fun ProtoseqSessionState.currentPage(): SequencerPageState {
    val selected = pages.getOrNull(selectedPageIndex)
    if (selected != null) return selected

    return pages.firstOrNull() ?: SequencerPageState(pageIndex = 0)
}
