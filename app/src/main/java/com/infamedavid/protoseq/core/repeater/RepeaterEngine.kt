package com.infamedavid.protoseq.core.repeater

import java.util.ArrayDeque

class RepeaterEngine {
    private var state: RptrState = RptrState.Idle
    private var bufferSnapshot: RptrBuffer? = null
    private var nextCaptureId: Int = 1

    fun press(division: RptrDivision, config: RptrConfig, currentTick: Long) {
        if (state != RptrState.Idle) {
            return
        }

        val windowTicks = computeWindowTicks(division = division, config = config)
        state = when (config.startMode) {
            RptrStartMode.FREE -> beginRecord(division = division, windowTicks = windowTicks, recordStartTick = currentTick)
            RptrStartMode.GRID -> {
                val recordStartTick = (((currentTick - 1) / windowTicks) + 1) * windowTicks + 1
                RptrState.Wait(
                    division = division,
                    windowTicks = windowTicks,
                    recordStartTick = recordStartTick,
                )
            }
        }
    }

    fun release(currentTick: Long): TickResult {
        val current = state
        return when (current) {
            is RptrState.Idle -> TickResult()
            is RptrState.Wait,
            is RptrState.Record,
            -> {
                state = RptrState.Idle
                bufferSnapshot = null
                TickResult()
            }

            is RptrState.Loop -> {
                val midi = clearActiveLoopNotes(current.activeLoopNotes)
                state = RptrState.Idle
                bufferSnapshot = null
                TickResult(midi = midi)
            }

            is RptrState.Release -> {
                state = RptrState.Idle
                bufferSnapshot = null
                TickResult()
            }
        }
    }

    fun onLiveNoteOn(event: MidiNoteOnEvent): LiveRouteResult {
        return when (val current = state) {
            is RptrState.Idle,
            is RptrState.Wait,
            -> LiveRouteResult(passThrough = true)

            is RptrState.Record -> {
                val withinWindow = event.tick >= current.recordStartTick && event.tick < current.recordEndTickExclusive
                if (withinWindow) {
                    val capture = CapturedOn(
                        id = nextCaptureId++,
                        channel = event.channel,
                        note = event.note,
                        velocity = event.velocity,
                        startOffsetTicks = (event.tick - current.recordStartTick).toInt(),
                    )
                    current.capturedOns.add(capture)
                    val key = NoteKey(channel = event.channel, note = event.note)
                    val queue = current.openByKey.getOrPut(key) { ArrayDeque() }
                    queue.offerLast(capture.id)
                }
                LiveRouteResult(passThrough = true)
            }

            is RptrState.Loop,
            is RptrState.Release,
            -> LiveRouteResult(passThrough = false)
        }
    }

    fun onLiveNoteOff(event: MidiNoteOffEvent): LiveRouteResult {
        return when (val current = state) {
            is RptrState.Idle,
            is RptrState.Wait,
            -> LiveRouteResult(passThrough = true)

            is RptrState.Record -> {
                val key = NoteKey(channel = event.channel, note = event.note)
                val queue = current.openByKey[key]
                val captureId = queue?.pollFirst()
                if (captureId != null) {
                    if (queue != null && queue.isEmpty()) {
                        current.openByKey.remove(key)
                    }
                    val endOffsetTicks =
                        (event.tick - current.recordStartTick).toInt().coerceAtMost(current.windowTicks)
                    current.capturedOns.firstOrNull { it.id == captureId }?.endOffsetTicks = endOffsetTicks
                }
                LiveRouteResult(passThrough = true)
            }

            is RptrState.Loop,
            is RptrState.Release,
            -> LiveRouteResult(passThrough = false)
        }
    }

    fun onTick(currentTick: Long): TickResult {
        return when (val current = state) {
            is RptrState.Idle,
            is RptrState.Release,
            -> TickResult()

            is RptrState.Wait -> {
                if (currentTick >= current.recordStartTick) {
                    state = beginRecord(
                        division = current.division,
                        windowTicks = current.windowTicks,
                        recordStartTick = current.recordStartTick,
                    )
                }
                TickResult()
            }

            is RptrState.Record -> {
                if (currentTick >= current.recordEndTickExclusive) {
                    val compileResult = compileBuffer(current)
                    enterLoop(
                        division = current.division,
                        buffer = compileResult.buffer,
                        loopStartTick = current.recordEndTickExclusive,
                    )
                    val loopMidi = renderLoopTick(state as RptrState.Loop, currentTick)
                    TickResult(midi = compileResult.cleanupMidi + loopMidi)
                } else {
                    TickResult()
                }
            }

            is RptrState.Loop -> TickResult(midi = renderLoopTick(current, currentTick))
        }
    }

    fun onTransportStop(currentTick: Long): TickResult {
        val current = state
        return when (current) {
            is RptrState.Loop -> {
                val midi = clearActiveLoopNotes(current.activeLoopNotes)
                state = RptrState.Idle
                bufferSnapshot = null
                TickResult(midi = midi)
            }

            is RptrState.Wait,
            is RptrState.Record,
            is RptrState.Release,
            is RptrState.Idle,
            -> {
                state = RptrState.Idle
                bufferSnapshot = null
                TickResult()
            }
        }
    }

    fun onTransportPause(currentTick: Long): TickResult = onTransportStop(currentTick)

    fun reset() {
        state = RptrState.Idle
        bufferSnapshot = null
        nextCaptureId = 1
    }

    fun getState(): RptrState = state

    fun getBufferSnapshotOrNull(): RptrBuffer? = bufferSnapshot

    private fun computeWindowTicks(division: RptrDivision, config: RptrConfig): Int {
        val sanitizedBaseUnits = config.baseUnits.coerceAtLeast(1)
        val wholeNoteTicks = config.clockPpqn * 4
        return (wholeNoteTicks * sanitizedBaseUnits) / division.denominator
    }

    private fun beginRecord(division: RptrDivision, windowTicks: Int, recordStartTick: Long): RptrState.Record {
        return RptrState.Record(
            division = division,
            windowTicks = windowTicks,
            recordStartTick = recordStartTick,
            recordEndTickExclusive = recordStartTick + windowTicks,
            capturedOns = mutableListOf(),
            openByKey = mutableMapOf(),
        )
    }

    private data class CompileResult(
        val buffer: RptrBuffer,
        val cleanupMidi: List<RptrMidiOut>,
    )

    private fun compileBuffer(record: RptrState.Record): CompileResult {
        val notes = mutableListOf<LoopNote>()
        val cleanupMidi = mutableListOf<RptrMidiOut>()

        for (captured in record.capturedOns) {
            val wasOpen = captured.endOffsetTicks == null
            val end = (captured.endOffsetTicks ?: record.windowTicks).let {
                if (it <= captured.startOffsetTicks) captured.startOffsetTicks + 1 else it
            }

            if (wasOpen) {
                cleanupMidi += RptrMidiOut.NoteOff(channel = captured.channel, note = captured.note)
            }

            notes += LoopNote(
                channel = captured.channel,
                note = captured.note,
                velocity = captured.velocity,
                startOffsetTicks = captured.startOffsetTicks,
                endOffsetTicks = end,
            )
        }

        notes.sortWith(compareBy<LoopNote>({ it.startOffsetTicks }, { it.note }, { it.channel }))

        return CompileResult(
            buffer = RptrBuffer(windowTicks = record.windowTicks, notes = notes),
            cleanupMidi = cleanupMidi,
        )
    }

    private fun enterLoop(division: RptrDivision, buffer: RptrBuffer, loopStartTick: Long) {
        bufferSnapshot = buffer
        state = RptrState.Loop(
            division = division,
            buffer = buffer,
            loopStartTick = loopStartTick,
            activeLoopNotes = mutableSetOf(),
        )
    }

    private fun clearActiveLoopNotes(activeLoopNotes: MutableSet<NoteKey>): List<RptrMidiOut> {
        val midi = activeLoopNotes
            .map { key -> RptrMidiOut.NoteOff(channel = key.channel, note = key.note) }
        activeLoopNotes.clear()
        return midi
    }

    private fun renderLoopTick(loop: RptrState.Loop, currentTick: Long): List<RptrMidiOut> {
        val buffer = loop.buffer
        if (buffer.notes.isEmpty()) {
            return emptyList()
        }

        val cycleElapsed = currentTick - loop.loopStartTick
        val offset = (cycleElapsed % buffer.windowTicks).toInt()
        val midi = mutableListOf<RptrMidiOut>()

        if (cycleElapsed > 0 && offset == 0) {
            midi += clearActiveLoopNotes(loop.activeLoopNotes)
        }

        for (note in buffer.notes) {
            if (note.endOffsetTicks == offset) {
                midi += RptrMidiOut.NoteOff(channel = note.channel, note = note.note)
                loop.activeLoopNotes.remove(NoteKey(channel = note.channel, note = note.note))
            }
        }

        for (note in buffer.notes) {
            if (note.startOffsetTicks == offset) {
                midi += RptrMidiOut.NoteOff(channel = note.channel, note = note.note)
                midi += RptrMidiOut.NoteOn(channel = note.channel, note = note.note, velocity = note.velocity)
                loop.activeLoopNotes.add(NoteKey(channel = note.channel, note = note.note))
            }
        }

        return midi
    }
}
