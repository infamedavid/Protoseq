package com.infamedavid.protoseq.core.repeater

import java.util.ArrayDeque

enum class RptrStartMode {
    FREE,
    GRID,
}

enum class RptrDivision(val denominator: Int) {
    D2(2),
    D4(4),
    D8(8),
    D16(16),
    D32(32),
    ;

    val label: String
        get() = "1/$denominator"
}

data class RptrConfig(
    val baseUnits: Int,
    val startMode: RptrStartMode,
    val clockPpqn: Int = 96,
)

data class MidiNoteOnEvent(
    val tick: Long,
    val channel: Int,
    val note: Int,
    val velocity: Int,
)

data class MidiNoteOffEvent(
    val tick: Long,
    val channel: Int,
    val note: Int,
)

sealed interface RptrMidiOut {
    data class NoteOn(val channel: Int, val note: Int, val velocity: Int) : RptrMidiOut
    data class NoteOff(val channel: Int, val note: Int) : RptrMidiOut
}

data class LoopNote(
    val channel: Int,
    val note: Int,
    val velocity: Int,
    val startOffsetTicks: Int,
    val endOffsetTicks: Int,
)

data class RptrBuffer(
    val windowTicks: Int,
    val notes: List<LoopNote>,
)

data class LiveRouteResult(
    val passThrough: Boolean,
    val extraMidi: List<RptrMidiOut> = emptyList(),
)

data class TickResult(
    val midi: List<RptrMidiOut> = emptyList(),
)

data class NoteKey(
    val channel: Int,
    val note: Int,
)

data class CapturedOn(
    val id: Int,
    val channel: Int,
    val note: Int,
    val velocity: Int,
    val startOffsetTicks: Int,
    var endOffsetTicks: Int? = null,
)

sealed interface RptrState {
    data object Idle : RptrState

    data class Wait(
        val division: RptrDivision,
        val windowTicks: Int,
        val recordStartTick: Long,
    ) : RptrState

    data class Record(
        val division: RptrDivision,
        val windowTicks: Int,
        val recordStartTick: Long,
        val recordEndTickExclusive: Long,
        val capturedOns: MutableList<CapturedOn>,
        val openByKey: MutableMap<NoteKey, ArrayDeque<Int>>,
    ) : RptrState

    data class Loop(
        val division: RptrDivision,
        val buffer: RptrBuffer,
        val loopStartTick: Long,
        val activeLoopNotes: MutableSet<NoteKey>,
    ) : RptrState

    data class Release(
        val notesToClear: Set<NoteKey>,
    ) : RptrState
}
