package com.infamedavid.protoseq.features.stochastic

import com.infamedavid.protoseq.core.music.ScaleQuantizer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class StochasticSequencerEngine(
    private val registerSize: Int = REGISTER_SIZE
) {
    private val register = IntArray(registerSize)
    private var currentIndex: Int = 0
    private var random: Random = Random.Default

    init {
        reset()
    }

    fun reset(seed: Long? = null) {
        random = seed?.let { Random(it) } ?: Random.Default
        for (i in register.indices) {
            register[i] = randomBit()
        }
        currentIndex = 0
    }

    fun getCurrentIndex(): Int = currentIndex

    fun getRegisterSnapshot(): List<Int> = register.toList()

    fun advance(config: StochasticSequencerConfig): StepOutput {
        val sanitized = config.sanitized(registerSize)
        val isDoubleMode = sanitized.lockPosition < -LOCK_DEADBAND

        val baseLength = sanitized.sequenceLength
        val effectiveLength = if (isDoubleMode) {
            (baseLength * 2).coerceAtMost(registerSize)
        } else {
            baseLength
        }

        val stepIndex = currentIndex % effectiveLength
        val editableIndex = stepIndex % baseLength

        updateEditableBit(editableIndex, sanitized.lockPosition)

        val rawValue = buildRawValue(
            startIndex = stepIndex,
            effectiveLength = effectiveLength,
            baseLength = baseLength,
            isDoubleMode = isDoubleMode
        )

        val note = when (sanitized.outputMode) {
            MidiOutputMode.NOTE -> mapNote(rawValue, sanitized)
            MidiOutputMode.CC -> null
        }

        val ccValue = when (sanitized.outputMode) {
            MidiOutputMode.NOTE -> null
            MidiOutputMode.CC -> mapCc(rawValue)
        }

        val gate = random.nextFloat() >= sanitized.bernoulliProbability
        val trigger = gate
        val gateLengthTicks = if (gate) {
            calculateGateLengthTicks(sanitized.gateLength, sanitized.randomGateLength)
        } else {
            0
        }

        currentIndex = (stepIndex + 1) % effectiveLength

        return StepOutput(
            stepIndex = stepIndex,
            effectiveLength = effectiveLength,
            rawValue = rawValue,
            note = note,
            ccValue = ccValue,
            gate = gate,
            trigger = trigger,
            gateLengthTicks = gateLengthTicks
        )
    }

    private fun updateEditableBit(editableIndex: Int, lockPosition: Float) {
        val absoluteLock = abs(lockPosition)

        if (absoluteLock < LOCK_DEADBAND) {
            register[editableIndex] = randomBit()
            return
        }

        val mutationProbability = ((1f - absoluteLock) / (1f - LOCK_DEADBAND)).coerceIn(0f, 1f)
        if (random.nextFloat() < mutationProbability) {
            register[editableIndex] = randomBit()
        }
    }

    private fun buildRawValue(
        startIndex: Int,
        effectiveLength: Int,
        baseLength: Int,
        isDoubleMode: Boolean
    ): Int {
        var value = 0
        repeat(RAW_WINDOW_SIZE) { offset ->
            val index = (startIndex + offset) % effectiveLength
            val bit = readBit(index, baseLength, isDoubleMode)
            value = (value shl 1) or bit
        }
        return value
    }

    private fun readBit(index: Int, baseLength: Int, isDoubleMode: Boolean): Int {
        if (!isDoubleMode || index < baseLength) {
            return register[index]
        }

        val mirroredIndex = index - baseLength
        return 1 - register[mirroredIndex]
    }

    private fun mapNote(rawValue: Int, config: StochasticSequencerConfig): Int {
        val semitoneRange = (config.pitchRangeOctaves * SEMITONES_PER_OCTAVE).coerceAtLeast(0)
        val mappedOffset = ((rawValue.toFloat() / RAW_MAX_VALUE.toFloat()) * semitoneRange)
            .roundToInt()
            .coerceIn(0, semitoneRange)

        val unquantizedNote = config.baseNote + mappedOffset + config.pitchOffset
        val quantized = ScaleQuantizer.quantize(unquantizedNote, config.quantizationMode)
        return quantized.coerceIn(MIDI_MIN, MIDI_MAX)
    }

    private fun mapCc(rawValue: Int): Int {
        return ((rawValue.toFloat() / RAW_MAX_VALUE.toFloat()) * MIDI_MAX.toFloat())
            .roundToInt()
            .coerceIn(MIDI_MIN, MIDI_MAX)
    }

    private fun calculateGateLengthTicks(gateLength: Float, randomGateLength: Float): Int {
        val baseTicks = gateLength * TICKS_PER_STEP
        val variationRange = randomGateLength * TICKS_PER_STEP
        val bipolarRandom = (random.nextFloat() * 2f) - 1f
        val randomizedTicks = baseTicks + (variationRange * bipolarRandom)
        return randomizedTicks.roundToInt().coerceIn(1, TICKS_PER_STEP)
    }

    private fun randomBit(): Int = if (random.nextBoolean()) 1 else 0

    private companion object {
        private const val REGISTER_SIZE = 32
        private const val LOCK_DEADBAND = 0.10f
        private const val RAW_WINDOW_SIZE = 8
        private const val RAW_MAX_VALUE = 255
        private const val TICKS_PER_STEP = 96
        private const val SEMITONES_PER_OCTAVE = 12
        private const val MIDI_MIN = 0
        private const val MIDI_MAX = 127
    }
}
