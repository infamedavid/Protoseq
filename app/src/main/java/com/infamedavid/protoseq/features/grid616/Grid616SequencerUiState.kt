package com.infamedavid.protoseq.features.grid616

const val GRID_616_TRACK_COUNT = 6
const val GRID_616_MAX_STEPS = 16
const val GRID_616_TICKS_PER_STEP = 24
const val GRID_616_GATE_TICKS = 3
const val GRID_616_MIN_TRACK_LENGTH = 1
const val GRID_616_MAX_TRACK_LENGTH = 16
const val GRID_616_MIN_DELAY_TICKS = 0
const val GRID_616_MAX_DELAY_TICKS = 23
const val GRID_616_MIN_VELOCITY = 1
const val GRID_616_MAX_VELOCITY = 127

private val DEFAULT_GRID_616_TRACK_NOTES = listOf(48, 49, 50, 51, 44, 45)

data class Grid616StepState(
    val enabled: Boolean = false,
    val velocity: Int = 100,
    val delayTicks: Int = 0,
)

fun Grid616StepState.normalized(): Grid616StepState =
    copy(
        velocity = velocity.coerceIn(GRID_616_MIN_VELOCITY, GRID_616_MAX_VELOCITY),
        delayTicks = delayTicks.coerceIn(GRID_616_MIN_DELAY_TICKS, GRID_616_MAX_DELAY_TICKS),
    )

fun defaultGrid616Steps(): List<Grid616StepState> =
    List(GRID_616_MAX_STEPS) { Grid616StepState() }

data class Grid616TrackState(
    val note: Int,
    val muted: Boolean = false,
    val length: Int = GRID_616_MAX_STEPS,
    val playbackMode: Grid616PlaybackMode = Grid616PlaybackMode.FORWARD,
    val steps: List<Grid616StepState> = defaultGrid616Steps(),
)

fun Grid616TrackState.normalized(): Grid616TrackState {
    val normalizedSteps = steps
        .map { it.normalized() }
        .take(GRID_616_MAX_STEPS)
        .let { existing ->
            existing + List((GRID_616_MAX_STEPS - existing.size).coerceAtLeast(0)) { Grid616StepState() }
        }

    return copy(
        note = note.coerceIn(0, 127),
        length = length.coerceIn(GRID_616_MIN_TRACK_LENGTH, GRID_616_MAX_TRACK_LENGTH),
        steps = normalizedSteps,
    )
}

fun defaultGrid616Tracks(): List<Grid616TrackState> =
    DEFAULT_GRID_616_TRACK_NOTES.map { note -> Grid616TrackState(note = note) }

data class Grid616SequencerUiState(
    val midiChannel: Int = 10,
    val swingAmount: Float = 0f,
    val tracks: List<Grid616TrackState> = defaultGrid616Tracks(),
)

fun Grid616SequencerUiState.normalized(): Grid616SequencerUiState {
    val normalizedTracks = tracks
        .map { it.normalized() }
        .take(GRID_616_TRACK_COUNT)
        .let { existing ->
            existing + defaultGrid616Tracks().drop(existing.size)
        }

    return copy(
        midiChannel = midiChannel.coerceIn(1, 16),
        swingAmount = swingAmount.coerceIn(0f, 0.75f),
        tracks = normalizedTracks,
    )
}
