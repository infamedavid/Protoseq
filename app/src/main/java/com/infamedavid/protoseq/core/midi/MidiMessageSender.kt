package com.infamedavid.protoseq.core.midi

interface MidiMessageSender {
    fun connect(target: MidiOutputTarget)
    fun disconnect()
    fun sendStart()
    fun sendStop()
    fun sendContinue()
    fun sendClock()
    fun sendNoteOn(channel: Int, note: Int, velocity: Int)
    fun sendNoteOff(channel: Int, note: Int, velocity: Int = 0)
}
