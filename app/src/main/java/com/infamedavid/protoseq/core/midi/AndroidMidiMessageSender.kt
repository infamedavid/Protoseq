package com.infamedavid.protoseq.core.midi

import android.media.midi.MidiDevice
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import java.io.IOException

class AndroidMidiMessageSender(
    private val midiManager: MidiManager
) : MidiMessageSender {

    private val lock = Any()

    private var currentDevice: MidiDevice? = null
    private var currentInputPort: MidiInputPort? = null

    override fun connect(target: MidiOutputTarget) {
        midiManager.openDevice(
            target.deviceInfo,
            object : MidiManager.OnDeviceOpenedListener {
                override fun onDeviceOpened(midiDevice: MidiDevice?) {
                    synchronized(lock) {
                        closeCurrentConnectionLocked()

                        if (midiDevice == null) {
                            return
                        }

                        val inputPort = midiDevice.openInputPort(target.inputPortNumber)
                        if (inputPort == null) {
                            midiDevice.closeQuietly()
                            return
                        }

                        currentDevice = midiDevice
                        currentInputPort = inputPort
                    }
                }
            },
            null
        )
    }

    override fun disconnect() {
        synchronized(lock) {
            closeCurrentConnectionLocked()
        }
    }

    override fun sendStart() {
        sendMessage(byteArrayOf(0xFA.toByte()))
    }

    override fun sendStop() {
        sendMessage(byteArrayOf(0xFC.toByte()))
    }

    override fun sendContinue() {
        sendMessage(byteArrayOf(0xFB.toByte()))
    }

    override fun sendClock() {
        sendMessage(byteArrayOf(0xF8.toByte()))
    }

    override fun sendNoteOn(channel: Int, note: Int, velocity: Int) {
        val status = (0x90 or ((channel.coerceIn(1, 16)) - 1)).toByte()
        sendMessage(
            byteArrayOf(
                status,
                note.coerceIn(0, 127).toByte(),
                velocity.coerceIn(0, 127).toByte()
            )
        )
    }

    override fun sendNoteOff(channel: Int, note: Int, velocity: Int) {
        val status = (0x80 or ((channel.coerceIn(1, 16)) - 1)).toByte()
        sendMessage(
            byteArrayOf(
                status,
                note.coerceIn(0, 127).toByte(),
                velocity.coerceIn(0, 127).toByte()
            )
        )
    }

    private fun sendMessage(data: ByteArray) {
        synchronized(lock) {
            val inputPort = currentInputPort ?: return
            try {
                inputPort.send(data, 0, data.size)
            } catch (_: IOException) {
                closeCurrentConnectionLocked()
            }
        }
    }

    private fun closeCurrentConnectionLocked() {
        currentInputPort.closeQuietly()
        currentInputPort = null

        currentDevice.closeQuietly()
        currentDevice = null
    }

    private fun MidiInputPort?.closeQuietly() {
        try {
            this?.close()
        } catch (_: IOException) {
            // Ignore close failures
        }
    }

    private fun MidiDevice?.closeQuietly() {
        try {
            this?.close()
        } catch (_: IOException) {
            // Ignore close failures
        }
    }
}