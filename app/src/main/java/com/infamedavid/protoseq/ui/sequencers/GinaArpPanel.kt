package com.infamedavid.protoseq.ui.sequencers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MAX_DEGREE
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MAX_OCTAVE
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MAX_ARP_LENGTH
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MAX_MIDI_CHANNEL
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MAX_NOTE_OFFSET
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MAX_STEP_DIVISIONS
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MAX_TEMPO_DIVISOR
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MAX_VELOCITY
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MIN_DEGREE
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MIN_OCTAVE
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MIN_ARP_LENGTH
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MIN_MIDI_CHANNEL
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MIN_NOTE_OFFSET
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MIN_STEP_DIVISIONS
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MIN_TEMPO_DIVISOR
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MIN_VELOCITY
import com.infamedavid.protoseq.features.ginaarp.GINA_ARP_MUTABLE_SEED
import com.infamedavid.protoseq.features.ginaarp.GinaArpMode
import com.infamedavid.protoseq.features.ginaarp.GinaArpPlayMode
import com.infamedavid.protoseq.features.ginaarp.GinaArpSequencerUiState
import com.infamedavid.protoseq.features.ginaarp.GinaArpStepState
import com.infamedavid.protoseq.features.ginaarp.normalized
import com.infamedavid.protoseq.features.ginaarp.updateStep
import com.infamedavid.protoseq.ui.components.ProtoControlShape
import com.infamedavid.protoseq.ui.components.ProtoSliderRow
import com.infamedavid.protoseq.ui.components.ProtoValueField

private val keyNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun GinaArpMode.shortLabel(): String = when (this) {
    GinaArpMode.MAJOR -> "MAJ"
    GinaArpMode.MINOR -> "MIN"
    GinaArpMode.DORIAN -> "DOR"
    GinaArpMode.MAJOR_PENTATONIC -> "PMAJ"
    GinaArpMode.MINOR_PENTATONIC -> "PMIN"
}

private fun GinaArpPlayMode.shortLabel(): String = when (this) {
    GinaArpPlayMode.FORWARD -> "FWD"
    GinaArpPlayMode.REVERSE -> "REV"
    GinaArpPlayMode.PING_PONG -> "PNG"
    GinaArpPlayMode.RANDOM -> "RND"
}

private fun formatSeedLabel(seed: Int): String =
    if (seed == GINA_ARP_MUTABLE_SEED) "MTB" else "%03d".format(seed)

@Composable
fun GinaArpPanel(
    state: GinaArpSequencerUiState,
    onStateChange: (GinaArpSequencerUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingStepIndex by remember { mutableStateOf<Int?>(null) }

    fun applyState(next: GinaArpSequencerUiState) {
        onStateChange(next.normalized())
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlobalControls(
            state = state,
            onStateChange = { applyState(it) }
        )

        GinaArpStepGrid(
            state = state,
            onToggleStep = { index ->
                applyState(
                    state.updateStep(index) { step ->
                        step.copy(enabled = !step.enabled)
                    }
                )
            },
            onEditStep = { index ->
                editingStepIndex = index
            }
        )
    }

    if (editingStepIndex != null) {
        val stepIndex = editingStepIndex ?: 0
        val step = state.steps.getOrNull(stepIndex) ?: GinaArpStepState()
        StepEditorDialog(
            stepIndex = stepIndex,
            initialStep = step,
            onDismiss = { editingStepIndex = null },
            onConfirm = { updatedStep ->
                applyState(state.updateStep(stepIndex) { updatedStep })
                editingStepIndex = null
            }
        )
    }
}

@Composable
private fun GlobalControls(
    state: GinaArpSequencerUiState,
    onStateChange: (GinaArpSequencerUiState) -> Unit,
) {
    var keyMenuExpanded by remember { mutableStateOf(false) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var playModeMenuExpanded by remember { mutableStateOf(false) }
    var seedMenuExpanded by remember { mutableStateOf(false) }
    var divisorMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ProtoValueField(
            label = "LENG",
            value = state.sequenceLength.toString(),
            onDecrement = {
                onStateChange(state.copy(sequenceLength = state.sequenceLength - 1))
            },
            onIncrement = {
                onStateChange(state.copy(sequenceLength = state.sequenceLength + 1))
            },
            modifier = Modifier.weight(1f)
        )

        Box(modifier = Modifier.weight(1f)) {
            ProtoValueField(
                label = "KEY",
                value = keyNames[state.keyRootSemitone],
                onClick = { keyMenuExpanded = true }
            )

            DropdownMenu(
                expanded = keyMenuExpanded,
                onDismissRequest = { keyMenuExpanded = false }
            ) {
                keyNames.forEachIndexed { keyIndex, keyName ->
                    DropdownMenuItem(
                        text = { Text(keyName) },
                        onClick = {
                            keyMenuExpanded = false
                            onStateChange(state.copy(keyRootSemitone = keyIndex))
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            ProtoValueField(
                label = "MODE",
                value = state.mode.shortLabel(),
                onClick = { modeMenuExpanded = true }
            )

            DropdownMenu(
                expanded = modeMenuExpanded,
                onDismissRequest = { modeMenuExpanded = false }
            ) {
                GinaArpMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.shortLabel()) },
                        onClick = {
                            modeMenuExpanded = false
                            onStateChange(state.copy(mode = mode))
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            ProtoValueField(
                label = "PLAY",
                value = state.playMode.shortLabel(),
                onClick = { playModeMenuExpanded = true }
            )

            DropdownMenu(
                expanded = playModeMenuExpanded,
                onDismissRequest = { playModeMenuExpanded = false }
            ) {
                GinaArpPlayMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.shortLabel()) },
                        onClick = {
                            playModeMenuExpanded = false
                            onStateChange(state.copy(playMode = mode))
                        }
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ProtoValueField(
                label = "SEED",
                value = formatSeedLabel(state.seed),
                onClick = { seedMenuExpanded = true },
            )
            DropdownMenu(
                expanded = seedMenuExpanded,
                onDismissRequest = { seedMenuExpanded = false }
            ) {
                (1..100).forEach { seed ->
                    DropdownMenuItem(
                        text = { Text(formatSeedLabel(seed)) },
                        onClick = {
                            seedMenuExpanded = false
                            onStateChange(state.copy(seed = seed))
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            ProtoValueField(
                label = "CLDV",
                value = state.tempoDivisor.toString(),
                onClick = { divisorMenuExpanded = true }
            )
            DropdownMenu(
                expanded = divisorMenuExpanded,
                onDismissRequest = { divisorMenuExpanded = false }
            ) {
                (GINA_ARP_MIN_TEMPO_DIVISOR..GINA_ARP_MAX_TEMPO_DIVISOR).forEach { divisor ->
                    DropdownMenuItem(
                        text = { Text(divisor.toString()) },
                        onClick = {
                            divisorMenuExpanded = false
                            onStateChange(state.copy(tempoDivisor = divisor))
                        }
                    )
                }
            }
        }

        ProtoValueField(
            label = "CHNL",
            value = state.midiChannel.toString(),
            onDecrement = {
                onStateChange(
                    state.copy(midiChannel = (state.midiChannel - 1).coerceAtLeast(GINA_ARP_MIN_MIDI_CHANNEL))
                )
            },
            onIncrement = {
                onStateChange(
                    state.copy(midiChannel = (state.midiChannel + 1).coerceAtMost(GINA_ARP_MAX_MIDI_CHANNEL))
                )
            },
            modifier = Modifier.weight(1f)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ProtoSliderRow(
                label = "OFFS",
                value = state.globalNoteOffset.toFloat(),
                valueText = when {
                    state.globalNoteOffset > 0 -> "+${state.globalNoteOffset}"
                    else -> state.globalNoteOffset.toString()
                },
                onValueChange = { onStateChange(state.copy(globalNoteOffset = it.toInt())) },
                valueRange = GINA_ARP_MIN_NOTE_OFFSET.toFloat()..GINA_ARP_MAX_NOTE_OFFSET.toFloat(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "-12", style = MaterialTheme.typography.labelSmall)
                Text(text = "0", style = MaterialTheme.typography.labelSmall)
                Text(text = "+12", style = MaterialTheme.typography.labelSmall)
            }
        }

        ProtoSliderRow(
            label = "RATIO",
            value = state.globalRatioMultiplier,
            valueText = "${(state.globalRatioMultiplier * 100).toInt()}%",
            onValueChange = { onStateChange(state.copy(globalRatioMultiplier = it)) },
            valueRange = -1f..1f,
            modifier = Modifier.weight(1f)
        )

        ProtoSliderRow(
            label = "GLEN",
            value = state.gateLength,
            valueText = "${(state.gateLength * 100).toInt()}%",
            onValueChange = { onStateChange(state.copy(gateLength = it)) },
            modifier = Modifier.weight(1f)
        )

        ProtoSliderRow(
            label = "RLEN",
            value = state.randomGateLength,
            valueText = "${(state.randomGateLength * 100).toInt()}%",
            onValueChange = { onStateChange(state.copy(randomGateLength = it)) },
            modifier = Modifier.weight(1f)
        )

        ProtoSliderRow(
            label = "BRNL",
            value = 1f - state.bernoulliGate,
            valueText = "${((1f - state.bernoulliGate) * 100).toInt()}%",
            onValueChange = { onStateChange(state.copy(bernoulliGate = 1f - it)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GinaArpStepGrid(
    state: GinaArpSequencerUiState,
    onToggleStep: (Int) -> Unit,
    onEditStep: (Int) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(2) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(4) { colIndex ->
                    val stepIndex = rowIndex * 4 + colIndex
                    val step = state.steps.getOrNull(stepIndex) ?: GinaArpStepState()
                    val inSequence = stepIndex < state.sequenceLength
                    val bgColor = if (step.enabled && inSequence) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (inSequence) 0.45f else 0.2f)
                    }
                    val labelColor = if (step.enabled && inSequence) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (inSequence) 1f else 0.5f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(ProtoControlShape)
                            .background(bgColor)
                            .border(1.dp, labelColor.copy(alpha = 0.5f), ProtoControlShape)
                            .combinedClickable(
                                onClick = { onToggleStep(stepIndex) },
                                onLongClick = { onEditStep(stepIndex) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = (stepIndex + 1).toString(),
                                color = labelColor,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (step.enabled) {
                                Text(
                                    text = "D${step.degree} O${step.octave}",
                                    color = labelColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Tap: toggle • Long press: edit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepEditorDialog(
    stepIndex: Int,
    initialStep: GinaArpStepState,
    onDismiss: () -> Unit,
    onConfirm: (GinaArpStepState) -> Unit,
) {
    var draft by remember(initialStep) { mutableStateOf(initialStep) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Step ${stepIndex + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProtoValueField(
                        label = "DEG",
                        value = draft.degree.toString(),
                        onDecrement = { draft = draft.copy(degree = draft.degree - 1) },
                        onIncrement = { draft = draft.copy(degree = draft.degree + 1) },
                        modifier = Modifier.weight(1f)
                    )
                    ProtoValueField(
                        label = "OCT",
                        value = draft.octave.toString(),
                        onDecrement = { draft = draft.copy(octave = draft.octave - 1) },
                        onIncrement = { draft = draft.copy(octave = draft.octave + 1) },
                        modifier = Modifier.weight(1f)
                    )
                    ProtoValueField(
                        label = "DIVS",
                        value = draft.divisions.toString(),
                        onDecrement = { draft = draft.copy(divisions = draft.divisions - 1) },
                        onIncrement = { draft = draft.copy(divisions = draft.divisions + 1) },
                        modifier = Modifier.weight(1f)
                    )
                    ProtoValueField(
                        label = "ALEN",
                        value = draft.arpLength.toString(),
                        onDecrement = { draft = draft.copy(arpLength = draft.arpLength - 1) },
                        onIncrement = { draft = draft.copy(arpLength = draft.arpLength + 1) },
                        modifier = Modifier.weight(1f)
                    )
                }

                ProtoSliderRow(
                    label = "RATIO",
                    value = draft.ratio,
                    valueText = "${(draft.ratio * 100).toInt()}%",
                    onValueChange = { ratio -> draft = draft.copy(ratio = ratio) }
                )

                ProtoSliderRow(
                    label = "VEL",
                    value = ((draft.velocity - GINA_ARP_MIN_VELOCITY).toFloat() /
                            (GINA_ARP_MAX_VELOCITY - GINA_ARP_MIN_VELOCITY).toFloat()),
                    valueText = draft.velocity.toString(),
                    onValueChange = { normalized ->
                        val mapped = GINA_ARP_MIN_VELOCITY +
                                (normalized * (GINA_ARP_MAX_VELOCITY - GINA_ARP_MIN_VELOCITY)).toInt()
                        draft = draft.copy(velocity = mapped)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        draft.copy(
                            degree = draft.degree.coerceIn(GINA_ARP_MIN_DEGREE, GINA_ARP_MAX_DEGREE),
                            octave = draft.octave.coerceIn(GINA_ARP_MIN_OCTAVE, GINA_ARP_MAX_OCTAVE),
                            divisions = draft.divisions.coerceIn(GINA_ARP_MIN_STEP_DIVISIONS, GINA_ARP_MAX_STEP_DIVISIONS),
                            arpLength = draft.arpLength.coerceIn(GINA_ARP_MIN_ARP_LENGTH, GINA_ARP_MAX_ARP_LENGTH),
                            velocity = draft.velocity.coerceIn(GINA_ARP_MIN_VELOCITY, GINA_ARP_MAX_VELOCITY),
                            ratio = draft.ratio.coerceIn(0f, 1f)
                        )
                    )
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
