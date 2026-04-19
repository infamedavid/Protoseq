package com.infamedavid.protoseq.core.midi

class NoOpMidiMessageSender : MidiMessageSender {
    override fun connect(target: MidiOutputTarget) = Unit
    override fun disconnect() = Unit
    override fun sendStart() = Unit
    override fun sendStop() = Unit
    override fun sendContinue() = Unit
    override fun sendClock() = Unit
    override fun sendNoteOn(channel: Int, note: Int, velocity: Int) = Unit
    override fun sendNoteOff(channel: Int, note: Int, velocity: Int) = Unit
}
