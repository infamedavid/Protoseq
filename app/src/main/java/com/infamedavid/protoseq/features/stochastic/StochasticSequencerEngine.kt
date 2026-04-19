package com.infamedavid.protoseq.features.stochastic

import com.infamedavid.protoseq.core.music.ScaleQuantizer
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

class StochasticSequencerEngine {
    private val register = IntArray(REGISTER_SIZE)
    private var phase: Int = 0
    private var random: Random = Random.Default

    init {
        reset()
    }

    fun reset(seed: Long? = null) {
        random = seed?.let(::Random) ?: Random.Default
        for (i in register.indices) {
            register[i] = randomBit()
        }
        phase = 0
    }

    fun getCurrentIndex(): Int = phase

    fun getRegisterSnapshot(): List<Int> = register.toList()

    fun advance(config: StochasticSequencerConfig): StepOutput {
        val sanitized = config.sanitized(REGISTER_SIZE)
        val activeLength = sanitized.sequenceLength
        val lock = sanitized.lockPosition

        val feedbackBit = register[activeLength - 1]
        val pFlip = 0.5f * (1f - lock)
        val flip = if (random.nextFloat() < pFlip) 1 else 0
        val incomingBit = feedbackBit xor flip

        for (i in activeLength - 1 downTo 1) {
            register[i] = register[i - 1]
        }
        register[0] = incomingBit

        var rawValue = 0
        for (i in 0 until RAW_WINDOW_SIZE) {
            rawValue = (rawValue shl 1) or register[i]
        }

        val note = if (sanitized.outputMode == MidiOutputMode.NOTE) {
            mapNote(rawValue, sanitized)
        } else {
            null
        }

        val ccValue = if (sanitized.outputMode == MidiOutputMode.CC) {
            mapCc(rawValue)
        } else {
            null
        }

        val gate = random.nextFloat() >= sanitized.bernoulliProbability
        val trigger = gate
        val gateLengthTicks = if (gate) {
            calculateGateLengthTicks(sanitized.gateLength, sanitized.randomGateLength)
        } else {
            0
        }

        val effectivePeriod = if (lock < 0f) activeLength * 2 else activeLength
        val currentPhase = phase
        phase = (phase + 1) % effectivePeriod

        return StepOutput(
            phase = currentPhase,
            effectivePeriod = effectivePeriod,
            rawValue = rawValue,
            note = note,
            ccValue = ccValue,
            gate = gate,
            trigger = trigger,
            gateLengthTicks = gateLengthTicks,
            registerSnapshot = register.toList(),
        )
    }

    private fun mapNote(rawValue: Int, config: StochasticSequencerConfig): Int {
        val semitoneRange = (config.pitchRangeOctaves * SEMITONES_PER_OCTAVE).coerceAtLeast(0)
        val mappedOffset = ((rawValue.toFloat() / RAW_MAX_VALUE.toFloat()) * semitoneRange)
            .roundToInt()
            .coerceIn(0, semitoneRange)

        val unquantizedNote = config.baseNote + config.pitchOffset + mappedOffset
        val quantized = ScaleQuantizer.quantize(unquantizedNote, config.quantizationMode)
        return quantized.coerceIn(MIDI_MIN, MIDI_MAX)
    }

    private fun mapCc(rawValue: Int): Int {
        return ((rawValue.toFloat() / RAW_MAX_VALUE.toFloat()) * MIDI_MAX.toFloat())
            .roundToInt()
            .coerceIn(MIDI_MIN, MIDI_MAX)
    }

    private fun calculateGateLengthTicks(gateLength: Float, randomGateLength: Float): Int {
        val normalizedGateLength = gateLength.coerceIn(0f, 1f)
        val baseTicks = 1 + floor(normalizedGateLength * (TICKS_PER_STEP - 1).toFloat()).toInt()

        val normalizedRandomGateLength = randomGateLength.coerceIn(0f, 1f)
        if (normalizedRandomGateLength == 0f) {
            return baseTicks
        }

        val maxDelta = floor(normalizedRandomGateLength * (TICKS_PER_STEP - 1).toFloat()).toInt()
        val randomDelta = random.nextInt(from = -maxDelta, until = maxDelta + 1)

        return (baseTicks + randomDelta).coerceIn(1, TICKS_PER_STEP)
    }

    private fun randomBit(): Int = if (random.nextBoolean()) 1 else 0

    private companion object {
        private const val REGISTER_SIZE = 16
        private const val RAW_WINDOW_SIZE = 8
        private const val RAW_MAX_VALUE = 255
        private const val TICKS_PER_STEP = 24
        private const val SEMITONES_PER_OCTAVE = 12
        private const val MIDI_MIN = 0
        private const val MIDI_MAX = 127
    }
}
