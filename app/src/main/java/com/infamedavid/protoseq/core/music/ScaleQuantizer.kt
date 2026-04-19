package com.infamedavid.protoseq.core.music

object ScaleQuantizer {
    fun quantize(note: Int, mode: QuantizationMode): Int {
        return when (mode) {
            QuantizationMode.NONE -> note
            QuantizationMode.CHRM -> note
            QuantizationMode.MAJR -> note
            QuantizationMode.MINR -> note
            QuantizationMode.PMAJ -> note
            QuantizationMode.PMIN -> note
        }
    }
}