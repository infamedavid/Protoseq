package com.infamedavid.protoseq.core.midi

class MidiEngine(
    private val messageSender: MidiMessageSender,
    private val midiDeviceRepository: MidiDeviceRepository
) {
    private var selectedTarget: MidiOutputTarget? = null

    fun refreshDevices() {
        val outputTargets = midiDeviceRepository.refreshDevices()
        if (outputTargets.isEmpty()) {
            selectedTarget = null
            messageSender.disconnect()
            return
        }

        val current = selectedTarget
        if (current == null || outputTargets.none { it.id == current.id && it.inputPortNumber == current.inputPortNumber }) {
            selectOutputTarget(outputTargets.first())
        }
    }

    fun getOutputTargets(): List<MidiOutputTarget> = midiDeviceRepository.getOutputTargets()

    fun selectOutputTarget(target: MidiOutputTarget) {
        selectedTarget = target
        messageSender.connect(target)
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
