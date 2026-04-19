package com.infamedavid.protoseq.features.stochastic

data class SequenceStep(
    val pitchValue: Int = 60,
    val gateState: Boolean = true
)
