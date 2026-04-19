package com.infamedavid.protoseq.core.clock

class ClockEngine(
    private val internalPpqn: Int = 96
) {
    private var bpm: Double = 120.0
    private var transportState: TransportState = TransportState.Stopped

    fun setBpm(value: Double) {
        bpm = value.coerceIn(20.0, 300.0)
    }

    fun getBpm(): Double = bpm

    fun getInternalPpqn(): Int = internalPpqn

    fun play() {
        transportState = TransportState.Playing
    }

    fun stop() {
        transportState = TransportState.Stopped
    }

    fun pause() {
        transportState = TransportState.Paused
    }

    fun getTransportState(): TransportState = transportState
}
