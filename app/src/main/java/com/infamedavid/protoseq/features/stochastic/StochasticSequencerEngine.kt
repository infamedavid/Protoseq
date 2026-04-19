package com.infamedavid.protoseq.features.stochastic

class StochasticSequencerEngine(
    private val maxSteps: Int = 32
) {
    private val steps = MutableList(maxSteps) { SequenceStep() }
    private var currentIndex: Int = 0

    fun getSteps(): List<SequenceStep> = steps

    fun getCurrentIndex(): Int = currentIndex

    fun advance() {
        currentIndex = (currentIndex + 1) % steps.size
    }
}
