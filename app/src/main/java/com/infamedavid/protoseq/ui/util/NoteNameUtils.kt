package com.infamedavid.protoseq.ui.util

private val NOTE_NAMES = listOf(
    "C", "C#", "D", "D#", "E", "F",
    "F#", "G", "G#", "A", "A#", "B"
)

fun midiNoteToDisplay(note: Int): String {
    val safeNote = note.coerceIn(0, 127)
    val noteName = NOTE_NAMES[safeNote % 12]
    val octave = (safeNote / 12) - 1
    return "$noteName$octave"
}