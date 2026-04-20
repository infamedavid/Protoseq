package com.infamedavid.protoseq.ui.util

import com.infamedavid.protoseq.core.midi.MidiOutputTarget
import java.util.Locale

fun buildMidiTargetShortLabels(targets: List<MidiOutputTarget>): Map<String, String> {
    val categoryCounters = mutableMapOf<String, Int>()
    val unknownCounters = mutableMapOf<String, Int>()
    val thruCount = targets.count { detectCategory(it.name) == MidiTargetCategory.THRU }
    var thruIndex = 0

    return targets.associate { target ->
        val category = detectCategory(target.name)
        val shortLabel = when (category) {
            MidiTargetCategory.USB -> nextIndexedLabel("USB", categoryCounters)
            MidiTargetCategory.BLUETOOTH -> nextIndexedLabel("BT", categoryCounters)
            MidiTargetCategory.NETWORK -> nextIndexedLabel("NET", categoryCounters)
            MidiTargetCategory.CLOCK -> nextIndexedLabel("CLK", categoryCounters)
            MidiTargetCategory.THRU -> {
                thruIndex += 1
                if (thruCount <= 1) "THRU" else "THR$thruIndex"
            }
            MidiTargetCategory.UNKNOWN -> {
                val base = buildUnknownBaseLabel(target.name)
                val index = unknownCounters.getOrDefault(base, 0) + 1
                unknownCounters[base] = index
                if (index == 1) base else "$base$index"
            }
        }
        target.selectionId to shortLabel
    }
}

private enum class MidiTargetCategory {
    USB, BLUETOOTH, NETWORK, CLOCK, THRU, UNKNOWN
}

private fun detectCategory(name: String): MidiTargetCategory {
    val lowered = name.lowercase(Locale.ROOT)
    return when {
        "usb" in lowered -> MidiTargetCategory.USB
        "bluetooth" in lowered || lowered.startsWith("bt") -> MidiTargetCategory.BLUETOOTH
        "network" in lowered ||
            "router" in lowered ||
            "rtp" in lowered ||
            "wifi" in lowered ||
            "wi-fi" in lowered ||
            " ip " in " $lowered " -> MidiTargetCategory.NETWORK
        "clock" in lowered || "clk" in lowered -> MidiTargetCategory.CLOCK
        "thru" in lowered || "through" in lowered -> MidiTargetCategory.THRU
        else -> MidiTargetCategory.UNKNOWN
    }
}

private fun nextIndexedLabel(prefix: String, counters: MutableMap<String, Int>): String {
    val next = counters.getOrDefault(prefix, 0) + 1
    counters[prefix] = next
    return "$prefix$next"
}

private fun buildUnknownBaseLabel(name: String): String {
    val words = name
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }

    val initials = words
        .take(4)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    if (initials.isNotBlank()) return initials

    val compact = name.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT)
    return compact.take(4).ifBlank { "OUT" }
}
