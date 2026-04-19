package com.infamedavid.protoseq.features.transport

import com.infamedavid.protoseq.core.clock.TransportState

data class TransportUiState(
    val bpm: Float = 120f,
    val state: TransportState = TransportState.Stopped
)
