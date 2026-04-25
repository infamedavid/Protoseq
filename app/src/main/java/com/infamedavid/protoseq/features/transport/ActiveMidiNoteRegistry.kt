package com.infamedavid.protoseq.features.transport

data class MidiNoteKey(
    val channel: Int,
    val note: Int,
)

internal class ActiveMidiNoteRegistry {
    private val ownershipByNote = mutableMapOf<MidiNoteKey, MutableMap<Int, Int>>()

    fun registerNoteOn(pageIndex: Int, channel: Int, note: Int) {
        val key = MidiNoteKey(channel = channel, note = note)
        val owners = ownershipByNote.getOrPut(key) { mutableMapOf() }
        owners[pageIndex] = (owners[pageIndex] ?: 0) + 1
    }

    fun releaseNote(pageIndex: Int, channel: Int, note: Int): Boolean {
        val key = MidiNoteKey(channel = channel, note = note)
        val owners = ownershipByNote[key] ?: return false
        val currentCount = owners[pageIndex] ?: return false

        if (currentCount <= 1) {
            owners.remove(pageIndex)
        } else {
            owners[pageIndex] = currentCount - 1
        }

        if (owners.isEmpty()) {
            ownershipByNote.remove(key)
            return true
        }

        return false
    }

    fun releasePage(pageIndex: Int): List<MidiNoteKey> {
        val releasable = mutableListOf<MidiNoteKey>()
        val iterator = ownershipByNote.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val owners = entry.value
            if (owners.remove(pageIndex) != null && owners.isEmpty()) {
                releasable += entry.key
                iterator.remove()
            }
        }
        return releasable
    }

    fun releaseAll(): List<MidiNoteKey> {
        val notes = ownershipByNote.keys.toList()
        ownershipByNote.clear()
        return notes
    }

    fun isEmpty(): Boolean = ownershipByNote.isEmpty()
}
