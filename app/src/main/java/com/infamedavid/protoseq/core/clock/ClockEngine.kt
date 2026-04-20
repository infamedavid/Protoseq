package com.infamedavid.protoseq.core.clock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class ClockEngine(
    private val internalPpqn: Int = 96,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _transportState = MutableStateFlow(TransportState.Stopped)
    val transportState: StateFlow<TransportState> = _transportState.asStateFlow()

    private val _tickCounter = MutableStateFlow(0L)
    val tickCounter: StateFlow<Long> = _tickCounter.asStateFlow()

    private val _ticks = MutableSharedFlow<Long>(extraBufferCapacity = 64)
    val ticks: SharedFlow<Long> = _ticks.asSharedFlow()

    private var bpm: Double = 120.0
    private var clockJob: Job? = null

    fun setBpm(value: Double) {
        bpm = value.coerceIn(1.0, 300.0)
    }

    fun getBpm(): Double = bpm

    fun getInternalPpqn(): Int = internalPpqn

    fun playFromStart() {
        if (_transportState.value == TransportState.Playing) return
        _tickCounter.value = 0L
        _transportState.value = TransportState.Playing
        startClockLoopIfNeeded()
    }

    fun resume() {
        if (_transportState.value != TransportState.Paused) return
        _transportState.value = TransportState.Playing
        startClockLoopIfNeeded()
    }

    fun stop() {
        if (_transportState.value == TransportState.Stopped) return
        stopClockLoop()
        _tickCounter.value = 0L
        _transportState.value = TransportState.Stopped
    }

    fun pause() {
        if (_transportState.value != TransportState.Playing) return
        stopClockLoop()
        _transportState.value = TransportState.Paused
    }

    fun getTransportState(): TransportState = _transportState.value

    private fun startClockLoopIfNeeded() {
        if (clockJob?.isActive == true) return
        clockJob = coroutineScope.launch {
            var nextTickAtNanos = System.nanoTime()
            while (isActive && _transportState.value == TransportState.Playing) {
                val tick = _tickCounter.value + 1L
                _tickCounter.value = tick
                _ticks.tryEmit(tick)

                val tickDurationNanos = secondsPerTick() * 1_000_000_000.0
                nextTickAtNanos += tickDurationNanos.toLong()

                val sleepNanos = max(0L, nextTickAtNanos - System.nanoTime())
                val sleepMillis = max(1L, sleepNanos / 1_000_000L)
                delay(sleepMillis)
            }
        }
    }

    private fun stopClockLoop() {
        clockJob?.cancel()
        clockJob = null
    }

    private fun secondsPerTick(): Double = 60.0 / (bpm * internalPpqn.toDouble())
}
