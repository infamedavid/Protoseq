package com.infamedavid.protoseq.core.music

object NoteUtils {
    fun clampMidiNote(note: Int): Int = note.coerceIn(0, 127)
    fun clampMidiChannel(channel: Int): Int = channel.coerceIn(1, 16)
}
