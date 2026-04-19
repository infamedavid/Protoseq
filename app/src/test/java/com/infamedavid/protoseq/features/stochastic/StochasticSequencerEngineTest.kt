package com.infamedavid.protoseq.features.stochastic

import com.infamedavid.protoseq.core.music.QuantizationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StochasticSequencerEngineTest {

    @Test
    fun hardRightLockRepeatsAfterBaseLength() {
        val engine = StochasticSequencerEngine()
        engine.reset(seed = 7L)

        val config = testConfig(length = 12, lock = 1f)
        val initial = engine.getRegisterSnapshot().take(config.sequenceLength)
        val outputs = MutableList(config.sequenceLength * 2) { engine.advance(config) }

        val afterOneLoop = engine.getRegisterSnapshot().take(config.sequenceLength)
        assertEquals(initial, afterOneLoop)

        val firstPass = outputs.take(config.sequenceLength).map { it.rawValue }
        val secondPass = outputs.drop(config.sequenceLength).map { it.rawValue }
        assertEquals(firstPass, secondPass)
    }

    @Test
    fun hardLeftLockInvertsAtNAndRepeatsAt2N() {
        val engine = StochasticSequencerEngine()
        engine.reset(seed = 13L)

        val config = testConfig(length = 10, lock = -1f)
        val initial = engine.getRegisterSnapshot().take(config.sequenceLength)

        repeat(config.sequenceLength) { engine.advance(config) }
        val afterN = engine.getRegisterSnapshot().take(config.sequenceLength)
        assertEquals(initial.map { 1 - it }, afterN)

        repeat(config.sequenceLength) { engine.advance(config) }
        val after2N = engine.getRegisterSnapshot().take(config.sequenceLength)
        assertEquals(initial, after2N)
    }

    @Test
    fun centerLockProducesBothFlipAndNoFlipEvents() {
        val engine = StochasticSequencerEngine()
        engine.reset(seed = 42L)

        val config = testConfig(length = 16, lock = 0f)
        var previous = engine.getRegisterSnapshot()
        var flips = 0
        var same = 0

        repeat(256) {
            val output = engine.advance(config)
            val prevFeedback = previous[config.sequenceLength - 1]
            val incoming = output.registerSnapshot[0]
            if (incoming == prevFeedback) same++ else flips++
            previous = output.registerSnapshot
        }

        assertTrue("Expected at least one flip at center lock", flips > 0)
        assertTrue("Expected at least one non-flip at center lock", same > 0)
    }

    @Test
    fun rightSlipBiasesTowardCopyingFeedback() {
        val engine = StochasticSequencerEngine()
        engine.reset(seed = 99L)

        val config = testConfig(length = 16, lock = 0.6f)
        val flipRate = observedFlipRate(engine, config, steps = 400)

        assertTrue("Flip rate should be below 0.5 on right slip", flipRate < 0.5)
        assertTrue("Flip rate should still be above 0 on right slip", flipRate > 0.0)
    }

    @Test
    fun leftSlipBiasesTowardInvertingFeedback() {
        val engine = StochasticSequencerEngine()
        engine.reset(seed = 123L)

        val config = testConfig(length = 16, lock = -0.6f)
        val flipRate = observedFlipRate(engine, config, steps = 400)

        assertTrue("Flip rate should be above 0.5 on left slip", flipRate > 0.5)
        assertTrue("Flip rate should be below 1 on left slip", flipRate < 1.0)
    }

    private fun observedFlipRate(
        engine: StochasticSequencerEngine,
        config: StochasticSequencerConfig,
        steps: Int,
    ): Double {
        var previous = engine.getRegisterSnapshot()
        var flips = 0

        repeat(steps) {
            val output = engine.advance(config)
            val prevFeedback = previous[config.sequenceLength - 1]
            val incoming = output.registerSnapshot[0]
            if (incoming != prevFeedback) {
                flips += 1
            }
            previous = output.registerSnapshot
        }

        return flips.toDouble() / steps.toDouble()
    }

    private fun testConfig(length: Int, lock: Float): StochasticSequencerConfig {
        return StochasticSequencerConfig(
            sequenceLength = length,
            lockPosition = lock,
            midiChannel = 1,
            outputMode = MidiOutputMode.NOTE,
            ccNumber = 1,
            baseNote = 60,
            quantizationMode = QuantizationMode.CHRM,
            slewAmount = 0f,
            pitchRangeOctaves = 2,
            pitchOffset = 0,
            gateLength = 0.5f,
            randomGateLength = 0f,
            bernoulliProbability = 0f,
        )
    }
}
