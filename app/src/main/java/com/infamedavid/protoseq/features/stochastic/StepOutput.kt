package com.infamedavid.protoseq.features.stochastic

data class StepOutput(
    val phase: Int,
    val effectivePeriod: Int,
    val rawValue: Int,
    val note: Int?,
    val ccValue: Int?,
    val gate: Boolean,
    val trigger: Boolean,
    val gateLengthTicks: Int,
    val registerSnapshot: List<Int>,
)
