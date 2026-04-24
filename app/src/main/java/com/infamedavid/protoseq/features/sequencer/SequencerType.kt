package com.infamedavid.protoseq.features.sequencer

enum class SequencerType(
    val label: String,
) {
    EMPTY(label = "Empty"),
    TURING_MACHINE(label = "Turing Machine"),
}
