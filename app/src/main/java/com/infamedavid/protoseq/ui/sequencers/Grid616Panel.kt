package com.infamedavid.protoseq.ui.sequencers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_DELAY_TICKS
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_STEPS
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_TRACK_LENGTH
import com.infamedavid.protoseq.features.grid616.GRID_616_MAX_VELOCITY
import com.infamedavid.protoseq.features.grid616.GRID_616_MIN_TRACK_LENGTH
import com.infamedavid.protoseq.features.grid616.GRID_616_MIN_VELOCITY
import com.infamedavid.protoseq.features.grid616.Grid616PlaybackMode
import com.infamedavid.protoseq.features.grid616.Grid616SequencerUiState
import com.infamedavid.protoseq.features.grid616.Grid616StepState
import com.infamedavid.protoseq.features.grid616.Grid616TrackState
import com.infamedavid.protoseq.features.grid616.normalized
import com.infamedavid.protoseq.ui.components.ProtoControlShape
import com.infamedavid.protoseq.ui.util.midiNoteToDisplay

private val Grid616StepCellSize = 20.dp
private val Grid616GridSpacing = 4.dp
private val Grid616StepNumberWidth = 34.dp
private val Grid616TrackControlWidth = 40.dp
private val Grid616TrackColumnWidth = 36.dp

@Composable
fun Grid616Panel(
    state: Grid616SequencerUiState,
    onStateChange: (Grid616SequencerUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingCell by remember { mutableStateOf<Grid616CellRef?>(null) }
    var velocityDraft by remember { mutableFloatStateOf(GRID_616_MIN_VELOCITY.toFloat()) }
    var delayDraft by remember { mutableFloatStateOf(0f) }
    var editingTrackNoteIndex by remember { mutableStateOf<Int?>(null) }
    var noteDraft by remember { mutableStateOf(60) }
    var editingTrackLengthIndex by remember { mutableStateOf<Int?>(null) }
    var lengthDraft by remember { mutableStateOf(GRID_616_MAX_STEPS) }
    var editingTrackModeIndex by remember { mutableStateOf<Int?>(null) }

    fun applyState(nextState: Grid616SequencerUiState) {
        onStateChange(nextState.normalized())
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top
        ) {
            CompactChannelControl(
                channel = state.midiChannel,
                onDecrement = {
                    val next = if (state.midiChannel <= 1) 16 else state.midiChannel - 1
                    applyState(state.copy(midiChannel = next))
                },
                onIncrement = {
                    val next = if (state.midiChannel >= 16) 1 else state.midiChannel + 1
                    applyState(state.copy(midiChannel = next))
                },
                modifier = Modifier.width(124.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SWING",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(state.swingAmount * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = state.swingAmount,
                    onValueChange = { value ->
                        applyState(state.copy(swingAmount = value.coerceIn(0f, 0.75f)))
                    },
                    valueRange = 0f..0.75f,
                    steps = 2,
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Grid616GridSpacing)
        ) {
            Spacer(modifier = Modifier.width(Grid616StepNumberWidth))
            state.tracks.forEachIndexed { trackIndex, track ->
                TrackTopControls(
                    trackIndex = trackIndex,
                    track = track,
                    onEditNote = {
                        editingTrackNoteIndex = trackIndex
                        noteDraft = track.note
                    },
                    onEditLength = {
                        editingTrackLengthIndex = trackIndex
                        lengthDraft = track.length
                    },
                    modifier = Modifier.width(Grid616TrackColumnWidth)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Grid616GridSpacing)) {
            repeat(GRID_616_MAX_STEPS) { stepIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Grid616GridSpacing)
                ) {
                    Text(
                        text = "%02d".format(stepIndex + 1),
                        modifier = Modifier.width(Grid616StepNumberWidth),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )

                    state.tracks.forEachIndexed { trackIndex, track ->
                        val step = track.steps[stepIndex]
                        val editable = stepIndex < track.length
                        StepCell(
                            enabled = step.enabled,
                            editable = editable,
                            onClick = {
                                if (!editable) return@StepCell
                                applyState(
                                    state.updateTrack(trackIndex) { trackState ->
                                        trackState.updateStep(stepIndex) { cell ->
                                            cell.copy(enabled = !cell.enabled)
                                        }
                                    }
                                )
                            },
                            onLongPress = {
                                if (!editable) return@StepCell
                                editingCell = Grid616CellRef(trackIndex = trackIndex, stepIndex = stepIndex)
                                velocityDraft = step.velocity.toFloat()
                                delayDraft = step.delayTicks.toFloat()
                            },
                            modifier = Modifier.width(Grid616TrackColumnWidth)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Grid616GridSpacing)
        ) {
            Spacer(modifier = Modifier.width(Grid616StepNumberWidth))
            state.tracks.forEachIndexed { trackIndex, track ->
                TrackBottomControls(
                    track = track,
                    modeMenuExpanded = editingTrackModeIndex == trackIndex,
                    onOpenModeMenu = { editingTrackModeIndex = trackIndex },
                    onDismissModeMenu = {
                        if (editingTrackModeIndex == trackIndex) {
                            editingTrackModeIndex = null
                        }
                    },
                    onSelectPlaybackMode = { selectedMode ->
                        applyState(
                            state.updateTrack(trackIndex) { trackState ->
                                trackState.copy(playbackMode = selectedMode)
                            }
                        )
                        editingTrackModeIndex = null
                    },
                    onToggleMute = {
                        applyState(state.updateTrack(trackIndex) { it.copy(muted = !it.muted) })
                    },
                    modifier = Modifier.width(Grid616TrackColumnWidth)
                )
            }
        }
    }

    val openEditor = editingCell
    if (openEditor != null) {
        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = {
                Text(text = "T${openEditor.trackIndex + 1} Step ${"%02d".format(openEditor.stepIndex + 1)}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Velocity: ${velocityDraft.toInt()}")
                    Slider(
                        value = velocityDraft,
                        onValueChange = {
                            velocityDraft = it.coerceIn(
                                GRID_616_MIN_VELOCITY.toFloat(),
                                GRID_616_MAX_VELOCITY.toFloat()
                            )
                        },
                        valueRange = GRID_616_MIN_VELOCITY.toFloat()..GRID_616_MAX_VELOCITY.toFloat(),
                        steps = GRID_616_MAX_VELOCITY - GRID_616_MIN_VELOCITY - 1
                    )

                    Text(text = "Delay: ${delayDraft.toInt()} ticks")
                    Slider(
                        value = delayDraft,
                        onValueChange = {
                            delayDraft = it.coerceIn(0f, GRID_616_MAX_DELAY_TICKS.toFloat())
                        },
                        valueRange = 0f..GRID_616_MAX_DELAY_TICKS.toFloat(),
                        steps = GRID_616_MAX_DELAY_TICKS - 1
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        applyState(
                            state.updateTrack(openEditor.trackIndex) { track ->
                                track.updateStep(openEditor.stepIndex) { step ->
                                    step.copy(
                                        velocity = velocityDraft.toInt().coerceIn(
                                            GRID_616_MIN_VELOCITY,
                                            GRID_616_MAX_VELOCITY
                                        ),
                                        delayTicks = delayDraft.toInt().coerceIn(
                                            0,
                                            GRID_616_MAX_DELAY_TICKS
                                        ),
                                    )
                                }
                            }
                        )
                        editingCell = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCell = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val openNoteEditorTrack = editingTrackNoteIndex
    if (openNoteEditorTrack != null) {
        AlertDialog(
            onDismissRequest = { editingTrackNoteIndex = null },
            title = { Text("Track ${openNoteEditorTrack + 1} Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = midiNoteToDisplay(noteDraft),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { noteDraft = (noteDraft - 12).coerceIn(0, 127) },
                            shape = ProtoControlShape
                        ) {
                            Text("-12")
                        }
                        OutlinedButton(
                            onClick = { noteDraft = (noteDraft - 1).coerceIn(0, 127) },
                            shape = ProtoControlShape
                        ) {
                            Text("-1")
                        }
                        OutlinedButton(
                            onClick = { noteDraft = (noteDraft + 1).coerceIn(0, 127) },
                            shape = ProtoControlShape
                        ) {
                            Text("+1")
                        }
                        OutlinedButton(
                            onClick = { noteDraft = (noteDraft + 12).coerceIn(0, 127) },
                            shape = ProtoControlShape
                        ) {
                            Text("+12")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        applyState(
                            state.updateTrack(openNoteEditorTrack) {
                                it.copy(note = noteDraft.coerceIn(0, 127))
                            }
                        )
                        editingTrackNoteIndex = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTrackNoteIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val openLengthEditorTrack = editingTrackLengthIndex
    if (openLengthEditorTrack != null) {
        AlertDialog(
            onDismissRequest = { editingTrackLengthIndex = null },
            title = { Text("Track ${openLengthEditorTrack + 1} Length") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "L$lengthDraft",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = lengthDraft.toFloat(),
                        onValueChange = {
                            lengthDraft = it.toInt().coerceIn(
                                GRID_616_MIN_TRACK_LENGTH,
                                GRID_616_MAX_TRACK_LENGTH
                            )
                        },
                        valueRange = GRID_616_MIN_TRACK_LENGTH.toFloat()..GRID_616_MAX_TRACK_LENGTH.toFloat(),
                        steps = GRID_616_MAX_TRACK_LENGTH - GRID_616_MIN_TRACK_LENGTH - 1
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        applyState(
                            state.updateTrack(openLengthEditorTrack) {
                                it.copy(
                                    length = lengthDraft.coerceIn(
                                        GRID_616_MIN_TRACK_LENGTH,
                                        GRID_616_MAX_TRACK_LENGTH
                                    )
                                )
                            }
                        )
                        editingTrackLengthIndex = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTrackLengthIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CompactChannelControl(
    channel: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CHNL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = channel.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onDecrement,
                shape = ProtoControlShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("-")
            }

            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            )

            OutlinedButton(
                onClick = onIncrement,
                shape = ProtoControlShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("+")
            }
        }
    }
}

@Composable
private fun TrackTopControls(
    trackIndex: Int,
    track: Grid616TrackState,
    onEditNote: () -> Unit,
    onEditLength: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "T${trackIndex + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        CompactClickableField(
            text = midiNoteToDisplay(track.note),
            onClick = onEditNote,
        )
        CompactClickableField(
            text = "L${track.length}",
            onClick = onEditLength,
        )
    }
}

@Composable
private fun TrackBottomControls(
    track: Grid616TrackState,
    modeMenuExpanded: Boolean,
    onOpenModeMenu: () -> Unit,
    onDismissModeMenu: () -> Unit,
    onSelectPlaybackMode: (Grid616PlaybackMode) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box {
            CompactClickableField(
                text = track.playbackMode.shortLabel(),
                onClick = onOpenModeMenu,
            )
            DropdownMenu(
                expanded = modeMenuExpanded,
                onDismissRequest = onDismissModeMenu
            ) {
                Grid616PlaybackMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.shortLabel()) },
                        onClick = { onSelectPlaybackMode(mode) }
                    )
                }
            }
        }
        CompactClickableField(
            text = "M",
            onClick = onToggleMute,
            active = track.muted
        )
    }
}

@Composable
private fun CompactClickableField(
    text: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val background = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val border = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .width(Grid616TrackControlWidth)
            .clip(ProtoControlShape)
            .background(background)
            .border(width = 1.dp, color = border, shape = ProtoControlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepCell(
    enabled: Boolean,
    editable: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        !editable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }

    val borderColor = when {
        !editable -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier.height(Grid616StepCellSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(Grid616StepCellSize)
                .clip(ProtoControlShape)
                .border(width = 1.dp, color = borderColor, shape = ProtoControlShape)
                .background(background)
                .combinedClickable(
                    enabled = editable,
                    onClick = onClick,
                    onLongClick = onLongPress,
                )
        )
    }
}

private data class Grid616CellRef(
    val trackIndex: Int,
    val stepIndex: Int,
)

private fun Grid616SequencerUiState.updateTrack(
    trackIndex: Int,
    transform: (Grid616TrackState) -> Grid616TrackState,
): Grid616SequencerUiState {
    val nextTracks = tracks.mapIndexed { index, trackState ->
        if (index == trackIndex) transform(trackState) else trackState
    }
    return copy(tracks = nextTracks)
}

private fun Grid616TrackState.updateStep(
    stepIndex: Int,
    transform: (Grid616StepState) -> Grid616StepState,
): Grid616TrackState {
    val nextSteps = steps.mapIndexed { index, stepState ->
        if (index == stepIndex) transform(stepState) else stepState
    }
    return copy(steps = nextSteps)
}

private fun Grid616PlaybackMode.shortLabel(): String = when (this) {
    Grid616PlaybackMode.FORWARD -> "FWD"
    Grid616PlaybackMode.REVERSE -> "REV"
    Grid616PlaybackMode.PING_PONG -> "PNG"
    Grid616PlaybackMode.RANDOM -> "RND"
}
