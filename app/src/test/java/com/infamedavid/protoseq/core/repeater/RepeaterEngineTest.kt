package com.infamedavid.protoseq.core.repeater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeaterEngineTest {

    @Test
    fun freeModeCapturesSingleNoteAndLoopsIt() {
        val engine = RepeaterEngine()
        val config = RptrConfig(baseUnits = 1, startMode = RptrStartMode.FREE)

        engine.press(division = RptrDivision.D16, config = config, currentTick = 1)
        engine.onLiveNoteOn(MidiNoteOnEvent(tick = 1, channel = 1, note = 60, velocity = 100))
        engine.onLiveNoteOff(MidiNoteOffEvent(tick = 5, channel = 1, note = 60))

        val closeResult = engine.onTick(currentTick = 25)
        assertTrue(closeResult.midi.isEmpty())

        val buffer = engine.getBufferSnapshotOrNull()
        requireNotNull(buffer)
        assertEquals(24, buffer.windowTicks)
        assertEquals(1, buffer.notes.size)
        assertEquals(0, buffer.notes[0].startOffsetTicks)
        assertEquals(4, buffer.notes[0].endOffsetTicks)
        assertTrue(engine.getState() is RptrState.Loop)

        val startTickMidi = engine.onTick(currentTick = 25).midi
        assertEquals(
            listOf(
                RptrMidiOut.NoteOff(channel = 1, note = 60),
                RptrMidiOut.NoteOn(channel = 1, note = 60, velocity = 100),
            ),
            startTickMidi,
        )

        val endTickMidi = engine.onTick(currentTick = 29).midi
        assertEquals(listOf(RptrMidiOut.NoteOff(channel = 1, note = 60)), endTickMidi)
    }

    @Test
    fun orphanNoteOffDoesNotCreateLoopMaterial() {
        val engine = RepeaterEngine()
        engine.press(RptrDivision.D16, RptrConfig(baseUnits = 1, startMode = RptrStartMode.FREE), currentTick = 1)

        engine.onLiveNoteOff(MidiNoteOffEvent(tick = 3, channel = 1, note = 64))
        engine.onTick(currentTick = 25)

        val buffer = engine.getBufferSnapshotOrNull()
        requireNotNull(buffer)
        assertTrue(buffer.notes.isEmpty())
        assertTrue(engine.getState() is RptrState.Loop)
        assertTrue(engine.onTick(currentTick = 30).midi.isEmpty())
    }

    @Test
    fun openNoteIsSyntheticallyClosedAtEndOfWindow() {
        val engine = RepeaterEngine()
        engine.press(RptrDivision.D16, RptrConfig(baseUnits = 1, startMode = RptrStartMode.FREE), currentTick = 1)

        engine.onLiveNoteOn(MidiNoteOnEvent(tick = 2, channel = 2, note = 67, velocity = 90))
        val closeResult = engine.onTick(currentTick = 25)

        assertEquals(listOf(RptrMidiOut.NoteOff(channel = 2, note = 67)), closeResult.midi)

        val buffer = engine.getBufferSnapshotOrNull()
        requireNotNull(buffer)
        assertEquals(1, buffer.notes.size)
        assertEquals(24, buffer.notes[0].endOffsetTicks)
    }

    @Test
    fun emptyCaptureBecomesSilentLoop() {
        val engine = RepeaterEngine()
        engine.press(RptrDivision.D16, RptrConfig(baseUnits = 1, startMode = RptrStartMode.FREE), currentTick = 1)

        engine.onTick(currentTick = 25)
        assertTrue(engine.getState() is RptrState.Loop)

        assertTrue(engine.onTick(currentTick = 26).midi.isEmpty())
        assertTrue(engine.onTick(currentTick = 40).midi.isEmpty())
        assertTrue(engine.onTick(currentTick = 49).midi.isEmpty())
    }

    @Test
    fun releaseDuringLoopClearsActiveLoopNotes() {
        val engine = RepeaterEngine()
        engine.press(RptrDivision.D16, RptrConfig(baseUnits = 1, startMode = RptrStartMode.FREE), currentTick = 1)
        engine.onLiveNoteOn(MidiNoteOnEvent(tick = 1, channel = 1, note = 60, velocity = 100))
        engine.onLiveNoteOff(MidiNoteOffEvent(tick = 5, channel = 1, note = 60))
        engine.onTick(currentTick = 25)

        engine.onTick(currentTick = 25)
        val releaseResult = engine.release(currentTick = 26)

        assertEquals(listOf(RptrMidiOut.NoteOff(channel = 1, note = 60)), releaseResult.midi)
        assertEquals(RptrState.Idle, engine.getState())
    }

    @Test
    fun gridModeWaitsForNextBoundary() {
        val engine = RepeaterEngine()
        val config = RptrConfig(baseUnits = 1, startMode = RptrStartMode.GRID)
        engine.press(RptrDivision.D16, config, currentTick = 7)

        val wait = engine.getState() as RptrState.Wait
        assertEquals(24, wait.windowTicks)
        assertEquals(25, wait.recordStartTick)

        engine.onTick(currentTick = 24)
        assertTrue(engine.getState() is RptrState.Wait)

        engine.onTick(currentTick = 25)
        val record = engine.getState() as RptrState.Record
        assertEquals(25, record.recordStartTick)
        assertEquals(49, record.recordEndTickExclusive)
    }

    @Test
    fun secondPressWhileActiveIsIgnored() {
        val engine = RepeaterEngine()
        engine.press(RptrDivision.D16, RptrConfig(baseUnits = 1, startMode = RptrStartMode.FREE), currentTick = 1)
        engine.press(RptrDivision.D8, RptrConfig(baseUnits = 2, startMode = RptrStartMode.GRID), currentTick = 3)

        val record = engine.getState() as RptrState.Record
        assertEquals(RptrDivision.D16, record.division)
        assertEquals(24, record.windowTicks)
        assertEquals(1, record.recordStartTick)
    }

    @Test
    fun releaseDuringRecordDiscardsIncompleteCapture() {
        val engine = RepeaterEngine()
        engine.press(RptrDivision.D16, RptrConfig(baseUnits = 1, startMode = RptrStartMode.FREE), currentTick = 1)
        engine.onLiveNoteOn(MidiNoteOnEvent(tick = 2, channel = 1, note = 60, velocity = 100))

        val releaseResult = engine.release(currentTick = 10)

        assertTrue(releaseResult.midi.isEmpty())
        assertEquals(RptrState.Idle, engine.getState())
        assertEquals(null, engine.getBufferSnapshotOrNull())
    }

    @Test
    fun transportStopClearsLoopOwnedNotes() {
        val engine = RepeaterEngine()
        engine.press(RptrDivision.D16, RptrConfig(baseUnits = 1, startMode = RptrStartMode.FREE), currentTick = 1)
        engine.onLiveNoteOn(MidiNoteOnEvent(tick = 1, channel = 1, note = 60, velocity = 100))
        engine.onLiveNoteOff(MidiNoteOffEvent(tick = 5, channel = 1, note = 60))
        engine.onTick(currentTick = 25)
        engine.onTick(currentTick = 25)

        val stopResult = engine.onTransportStop(currentTick = 26)

        assertEquals(listOf(RptrMidiOut.NoteOff(channel = 1, note = 60)), stopResult.midi)
        assertEquals(RptrState.Idle, engine.getState())
        assertEquals(null, engine.getBufferSnapshotOrNull())
    }
}
