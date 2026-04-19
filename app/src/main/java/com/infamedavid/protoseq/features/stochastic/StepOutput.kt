package com.infamedavid.protoseq.features.stochastic

data class StepOutput(
    val stepIndex: Int,
    val effectiveLength: Int,
    val rawValue: Int,
    val note: Int?,
    val ccValue: Int?,
    val gate: Boolean,
    val trigger: Boolean,
    val gateLengthTicks: Int,
)
