package com.infamedavid.protoseq.core.midi

class MidiEngine(
    private val messageSender: MidiMessageSender,
    private val midiDeviceRepository: MidiDeviceRepository
) {
    private var selectedTarget: MidiOutputTarget? = null

    fun startDeviceMonitoring(onTargetsUpdated: (List<MidiOutputTarget>, MidiOutputTarget?) -> Unit) {
        midiDeviceRepository.startMonitoring {
            val outputTargets = refreshDevices()
            onTargetsUpdated(outputTargets, selectedTarget)
        }
    }

    fun stopDeviceMonitoring() {
        midiDeviceRepository.stopMonitoring()
    }

    fun refreshDevices(): List<MidiOutputTarget> {
        val outputTargets = midiDeviceRepository.refreshDevices()
        if (outputTargets.isEmpty()) {
            clearSelection()
            return outputTargets
        }

        val current = selectedTarget
        val hasCurrent = current != null && outputTargets.any {
            it.selectionId == current.selectionId
        }

        when {
            hasCurrent -> Unit
            outputTargets.size == 1 -> selectOutputTarget(outputTargets.first())
            else -> clearSelection()
        }

        return outputTargets
    }

    fun getOutputTargets(): List<MidiOutputTarget> = midiDeviceRepository.getOutputTargets()

    fun getSelectedOutputTarget(): MidiOutputTarget? = selectedTarget

    fun clearSelection() {
        selectedTarget = null
        messageSender.disconnect()
    }

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

    fun sendNoteOn(channel: Int, note: Int, velocity: Int = 100) {
        messageSender.sendNoteOn(channel, note, velocity)
    }

    fun sendNoteOff(channel: Int, note: Int, velocity: Int = 0) {
        messageSender.sendNoteOff(channel, note, velocity)
    }
}
