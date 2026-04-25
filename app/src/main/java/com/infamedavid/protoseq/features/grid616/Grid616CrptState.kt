package com.infamedavid.protoseq.features.grid616

import kotlin.random.Random

data class Grid616CrptState(
    val rndmAmount: Float = 0f,
    val snapshot: Grid616CrptSnapshot? = null,
)

data class Grid616CrptSnapshot(
    val tracks: List<Grid616CrptTrackSnapshot>,
)

data class Grid616CrptTrackSnapshot(
    val note: Int,
    val length: Int,
    val playbackMode: Grid616PlaybackMode,
    val steps: List<Grid616CrptStepSnapshot>,
)

data class Grid616CrptStepSnapshot(
    val enabled: Boolean,
    val velocity: Int,
    val delayTicks: Int,
)

fun Grid616CrptStepSnapshot.normalized(): Grid616CrptStepSnapshot =
    copy(
        velocity = velocity.coerceIn(GRID_616_MIN_VELOCITY, GRID_616_MAX_VELOCITY),
        delayTicks = delayTicks.coerceIn(GRID_616_MIN_DELAY_TICKS, GRID_616_MAX_DELAY_TICKS),
    )

fun Grid616CrptTrackSnapshot.normalized(): Grid616CrptTrackSnapshot {
    val normalizedSteps = steps
        .map { it.normalized() }
        .take(GRID_616_MAX_STEPS)
        .let { existing ->
            existing + List((GRID_616_MAX_STEPS - existing.size).coerceAtLeast(0)) {
                Grid616CrptStepSnapshot(
                    enabled = false,
                    velocity = Grid616StepState().velocity,
                    delayTicks = Grid616StepState().delayTicks,
                )
            }
        }

    return copy(
        note = note.coerceIn(0, 127),
        length = length.coerceIn(GRID_616_MIN_TRACK_LENGTH, GRID_616_MAX_TRACK_LENGTH),
        steps = normalizedSteps,
    )
}

fun Grid616CrptSnapshot.normalized(): Grid616CrptSnapshot {
    val normalizedTracks = tracks
        .map { it.normalized() }
        .take(GRID_616_TRACK_COUNT)
        .let { existing ->
            existing + defaultGrid616Tracks().drop(existing.size).map { track ->
                Grid616CrptTrackSnapshot(
                    note = track.note,
                    length = track.length,
                    playbackMode = track.playbackMode,
                    steps = track.steps.map { step ->
                        Grid616CrptStepSnapshot(
                            enabled = step.enabled,
                            velocity = step.velocity,
                            delayTicks = step.delayTicks,
                        )
                    },
                )
            }
        }

    return copy(tracks = normalizedTracks)
}

fun Grid616CrptState.normalized(): Grid616CrptState =
    copy(
        rndmAmount = rndmAmount.coerceIn(0f, 1f),
        snapshot = snapshot?.normalized(),
    )

fun Grid616SequencerUiState.toCrptSnapshot(): Grid616CrptSnapshot =
    Grid616CrptSnapshot(
        tracks = tracks.map { track ->
            Grid616CrptTrackSnapshot(
                note = track.note,
                length = track.length,
                playbackMode = track.playbackMode,
                steps = track.steps.map { step ->
                    Grid616CrptStepSnapshot(
                        enabled = step.enabled,
                        velocity = step.velocity,
                        delayTicks = step.delayTicks,
                    )
                },
            )
        },
    ).normalized()

fun Grid616SequencerUiState.applyCrptSnapshot(
    snapshot: Grid616CrptSnapshot,
): Grid616SequencerUiState {
    val baseState = normalized()
    val normalizedSnapshot = snapshot.normalized()
    val mergedTracks = baseState.tracks.mapIndexed { trackIndex, currentTrack ->
        val snapshotTrack = normalizedSnapshot.tracks[trackIndex]
        currentTrack.copy(
            note = snapshotTrack.note,
            length = snapshotTrack.length,
            playbackMode = snapshotTrack.playbackMode,
            steps = snapshotTrack.steps.map { step ->
                Grid616StepState(
                    enabled = step.enabled,
                    velocity = step.velocity,
                    delayTicks = step.delayTicks,
                )
            },
        )
    }

    return baseState.copy(tracks = mergedTracks).normalized()
}

fun Grid616SequencerUiState.applyCrptSnapshotWithMutation(
    snapshot: Grid616CrptSnapshot,
    rndmAmount: Float,
    random: Random = Random.Default,
): Grid616SequencerUiState {
    val baseState = normalized()
    val rndm = rndmAmount.coerceIn(0f, 1f)
    if (rndm <= 0f) {
        return applyCrptSnapshot(snapshot)
    }

    val normalizedSnapshot = snapshot.normalized()
    val playbackModes = Grid616PlaybackMode.entries

    val mergedTracks = baseState.tracks.mapIndexed { trackIndex, currentTrack ->
        val snapshotTrack = normalizedSnapshot.tracks[trackIndex]

        currentTrack.copy(
            note = if (random.nextFloat() < rndm * 0.10f) random.nextInt(0, 128) else snapshotTrack.note,
            length = if (random.nextFloat() < rndm * 0.20f) {
                random.nextInt(GRID_616_MIN_TRACK_LENGTH, GRID_616_MAX_TRACK_LENGTH + 1)
            } else {
                snapshotTrack.length
            },
            playbackMode = if (random.nextFloat() < rndm * 0.35f) {
                playbackModes[random.nextInt(playbackModes.size)]
            } else {
                snapshotTrack.playbackMode
            },
            steps = snapshotTrack.steps.map { step ->
                Grid616StepState(
                    enabled = if (random.nextFloat() < rndm) random.nextBoolean() else step.enabled,
                    velocity = if (random.nextFloat() < rndm * 0.80f) {
                        random.nextInt(GRID_616_MIN_VELOCITY, GRID_616_MAX_VELOCITY + 1)
                    } else {
                        step.velocity
                    },
                    delayTicks = if (random.nextFloat() < rndm * 0.70f) {
                        random.nextInt(GRID_616_MIN_DELAY_TICKS, GRID_616_MAX_DELAY_TICKS + 1)
                    } else {
                        step.delayTicks
                    },
                )
            },
        )
    }

    return baseState.copy(tracks = mergedTracks).normalized()
}

fun Grid616SequencerUiState.withSavedCrptSnapshot(): Grid616SequencerUiState =
    copy(
        crptState = crptState.copy(snapshot = toCrptSnapshot()),
    ).normalized()

fun Grid616SequencerUiState.withAppliedCrptSnapshot(): Grid616SequencerUiState =
    withAppliedCrptSnapshot(Random.Default)

fun Grid616SequencerUiState.withAppliedCrptSnapshot(
    random: Random,
): Grid616SequencerUiState {
    val snapshot = crptState.snapshot ?: return normalized()
    return applyCrptSnapshotWithMutation(
        snapshot = snapshot,
        rndmAmount = crptState.rndmAmount,
        random = random,
    )
}
