package com.infamedavid.protoseq.features.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveMidiNoteRegistryTest {

    @Test
    fun singlePageNoteOnThenReleaseRequestsActualNoteOff() {
        val registry = ActiveMidiNoteRegistry()

        registry.registerNoteOn(pageIndex = 1, channel = 1, note = 60)

        assertTrue(registry.releaseNote(pageIndex = 1, channel = 1, note = 60))
        assertTrue(registry.isEmpty())
    }

    @Test
    fun twoPagesOwningSameNoteReleasesOnlyAfterLastOwner() {
        val registry = ActiveMidiNoteRegistry()

        registry.registerNoteOn(pageIndex = 1, channel = 1, note = 60)
        registry.registerNoteOn(pageIndex = 2, channel = 1, note = 60)

        assertFalse(registry.releaseNote(pageIndex = 1, channel = 1, note = 60))
        assertTrue(registry.releaseNote(pageIndex = 2, channel = 1, note = 60))
        assertTrue(registry.isEmpty())
    }

    @Test
    fun samePageOwningSameNoteTwiceReleasesAfterSecondRelease() {
        val registry = ActiveMidiNoteRegistry()

        registry.registerNoteOn(pageIndex = 1, channel = 1, note = 60)
        registry.registerNoteOn(pageIndex = 1, channel = 1, note = 60)

        assertFalse(registry.releaseNote(pageIndex = 1, channel = 1, note = 60))
        assertTrue(registry.releaseNote(pageIndex = 1, channel = 1, note = 60))
        assertTrue(registry.isEmpty())
    }

    @Test
    fun releasePageOnlyReleasesThatPage() {
        val registry = ActiveMidiNoteRegistry()

        registry.registerNoteOn(pageIndex = 1, channel = 1, note = 60)
        registry.registerNoteOn(pageIndex = 2, channel = 1, note = 60)
        registry.registerNoteOn(pageIndex = 1, channel = 2, note = 61)

        assertEquals(listOf(MidiNoteKey(channel = 2, note = 61)), registry.releasePage(pageIndex = 1))
        assertTrue(registry.releaseNote(pageIndex = 2, channel = 1, note = 60))
        assertTrue(registry.isEmpty())
    }

    @Test
    fun releaseAllReturnsAllActiveKeysAndClearsRegistry() {
        val registry = ActiveMidiNoteRegistry()

        registry.registerNoteOn(pageIndex = 1, channel = 1, note = 60)
        registry.registerNoteOn(pageIndex = 2, channel = 2, note = 61)

        val released = registry.releaseAll().toSet()

        assertEquals(
            setOf(
                MidiNoteKey(channel = 1, note = 60),
                MidiNoteKey(channel = 2, note = 61)
            ),
            released
        )
        assertTrue(registry.isEmpty())
    }

    @Test
    fun releasingUnownedNoteDoesNotCrashOrRequestNoteOff() {
        val registry = ActiveMidiNoteRegistry()

        assertFalse(registry.releaseNote(pageIndex = 1, channel = 1, note = 60))
        assertTrue(registry.releasePage(pageIndex = 1).isEmpty())
        assertTrue(registry.releaseAll().isEmpty())
    }
}
