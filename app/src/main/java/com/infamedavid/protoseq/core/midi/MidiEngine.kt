package com.infamedavid.protoseq.core.midi

class MidiEngine(
    private val messageSender: MidiMessageSender,
    private val midiDeviceRepository: MidiDeviceRepository
) {
    fun refreshDevices() {
        midiDeviceRepository.refreshDevices()
    }

    fun sendStart() {
        messageSender.sendStart()
    }

    fun sendStop() {
        messageSender.sendStop()
    }

    fun sendContinue() {
        messageSender.sendContinue()
    }

    fun sendClock() {
        messageSender.sendClock()
    }
}
