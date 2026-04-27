package com.infamedavid.protoseq.features.sequencer

enum class SequencerType(
    val label: String,
) {
    EMPTY(label = "Empty"),
    TURING_MACHINE(label = "Turing Machine"),
    GRID_616(label = "GRID 616"),
    GINAS_ARP(label = "Gina's ARP"),
    STP_116(label = "STP 116"),
}
