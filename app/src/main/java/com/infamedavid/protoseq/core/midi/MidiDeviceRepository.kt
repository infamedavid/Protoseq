package com.infamedavid.protoseq.core.midi

import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build

class MidiDeviceRepository(
    private val midiManager: MidiManager? = null
) {
    private var outputTargets: List<MidiOutputTarget> = emptyList()

    fun refreshDevices(): List<MidiOutputTarget> {
        val manager = midiManager ?: run {
            outputTargets = emptyList()
            return outputTargets
        }

        val devices: List<MidiDeviceInfo> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
            } else {
                manager.devices.toList()
            }

        outputTargets = devices.flatMap { deviceInfo: MidiDeviceInfo ->
            (0 until deviceInfo.inputPortCount).map { portNumber: Int ->
                MidiOutputTarget(
                    deviceInfo = deviceInfo,
                    inputPortNumber = portNumber
                )
            }
        }

        return outputTargets
    }

    fun getOutputTargets(): List<MidiOutputTarget> = outputTargets
}

data class MidiOutputTarget(
    val deviceInfo: MidiDeviceInfo,
    val inputPortNumber: Int
) {
    val id: Int = deviceInfo.id

    val name: String =
        deviceInfo.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: "MIDI Device $id"
}