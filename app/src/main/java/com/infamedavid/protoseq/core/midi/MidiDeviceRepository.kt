package com.infamedavid.protoseq.core.midi

import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

class MidiDeviceRepository(
    private val midiManager: MidiManager? = null
) {
    private var outputTargets: List<MidiOutputTarget> = emptyList()

    private val callbackExecutor = Executor { runnable -> runnable.run() }
    private val callbackHandler = Handler(Looper.getMainLooper())

    private var onDevicesChanged: ((List<MidiOutputTarget>) -> Unit)? = null
    private var deviceCallback: MidiManager.DeviceCallback? = null

    fun startMonitoring(onChanged: (List<MidiOutputTarget>) -> Unit) {
        onDevicesChanged = onChanged

        val manager = midiManager ?: run {
            outputTargets = emptyList()
            onDevicesChanged?.invoke(outputTargets)
            return
        }

        if (deviceCallback == null) {
            deviceCallback = object : MidiManager.DeviceCallback() {
                override fun onDeviceAdded(device: MidiDeviceInfo) {
                    notifyDeviceChange()
                }

                override fun onDeviceRemoved(device: MidiDeviceInfo) {
                    notifyDeviceChange()
                }

                override fun onDeviceStatusChanged(status: android.media.midi.MidiDeviceStatus) {
                    notifyDeviceChange()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.registerDeviceCallback(
                    MidiManager.TRANSPORT_MIDI_BYTE_STREAM,
                    callbackExecutor,
                    deviceCallback
                )
            } else {
                manager.registerDeviceCallback(deviceCallback, callbackHandler)
            }
        }

        notifyDeviceChange()
    }

    fun stopMonitoring() {
        val manager = midiManager ?: return
        val callback = deviceCallback ?: return
        manager.unregisterDeviceCallback(callback)
        deviceCallback = null
        onDevicesChanged = null
    }

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

    private fun notifyDeviceChange() {
        onDevicesChanged?.invoke(refreshDevices())
    }
}

data class MidiOutputTarget(
    val deviceInfo: MidiDeviceInfo,
    val inputPortNumber: Int
) {
    val id: Int = deviceInfo.id

    val selectionId: String = "$id:$inputPortNumber"

    val name: String =
        deviceInfo.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: "MIDI Device $id"
}
