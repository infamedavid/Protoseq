package com.infamedavid.protoseq.features.grid616

data class Grid616StepConfig(
    val enabled: Boolean,
    val velocity: Int,
    val delayTicks: Int,
)

data class Grid616TrackConfig(
    val note: Int,
    val muted: Boolean,
    val length: Int,
    val playbackMode: Grid616PlaybackMode,
    val steps: List<Grid616StepConfig>,
)

data class Grid616SequencerConfig(
    val midiChannel: Int,
    val swingAmount: Float,
    val tracks: List<Grid616TrackConfig>,
)

fun Grid616SequencerUiState.toConfig(): Grid616SequencerConfig {
    val normalized = normalized()
    return Grid616SequencerConfig(
        midiChannel = normalized.midiChannel,
        swingAmount = normalized.swingAmount,
        tracks = normalized.tracks.map { track ->
            Grid616TrackConfig(
                note = track.note,
                muted = track.muted,
                length = track.length,
                playbackMode = track.playbackMode,
                steps = track.steps.map { step ->
                    Grid616StepConfig(
                        enabled = step.enabled,
                        velocity = step.velocity,
                        delayTicks = step.delayTicks,
                    )
                },
            )
        },
    )
}
