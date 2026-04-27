package com.infamedavid.protoseq.ui.sequencers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infamedavid.protoseq.features.stp116.STP_116_MAX_GATE_DELAY_TICKS
import com.infamedavid.protoseq.features.stp116.STP_116_MAX_OCTAVE
import com.infamedavid.protoseq.features.stp116.STP_116_MAX_PITCH_CLASS
import com.infamedavid.protoseq.features.stp116.STP_116_MAX_VELOCITY
import com.infamedavid.protoseq.features.stp116.STP_116_MIN_FINE_TUNE_CENTS
import com.infamedavid.protoseq.features.stp116.STP_116_MIN_GATE_LENGTH_TICKS
import com.infamedavid.protoseq.features.stp116.STP_116_MIN_OCTAVE
import com.infamedavid.protoseq.features.stp116.STP_116_MIN_PITCH_CLASS
import com.infamedavid.protoseq.features.stp116.STP_116_MIN_VELOCITY
import com.infamedavid.protoseq.features.stp116.STP_116_STEP_COUNT
import com.infamedavid.protoseq.features.stp116.Stp116PlaybackMode
import com.infamedavid.protoseq.features.stp116.Stp116SequencerUiState
import com.infamedavid.protoseq.features.stp116.Stp116StepState
import com.infamedavid.protoseq.features.stp116.maxStp116GateLengthForDelay
import com.infamedavid.protoseq.features.stp116.normalized
import com.infamedavid.protoseq.features.stp116.updateStep
import com.infamedavid.protoseq.ui.components.ProtoControlShape
import com.infamedavid.protoseq.ui.components.ProtoValueField

private val STP_116_NOTE_NAMES = listOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
)

private fun Stp116PlaybackMode.shortLabel(): String = when (this) {
    Stp116PlaybackMode.FORWARD -> "FWD"
    Stp116PlaybackMode.REVERSE -> "REV"
    Stp116PlaybackMode.PING_PONG -> "PING"
    Stp116PlaybackMode.RANDOM -> "RAND"
    Stp116PlaybackMode.CENTRIC -> "CENT"
}

@Composable
fun Stp116Panel(
    state: Stp116SequencerUiState,
    onStateChange: (Stp116SequencerUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingStepIndex by remember { mutableStateOf<Int?>(null) }

    fun applyState(nextState: Stp116SequencerUiState) {
        onStateChange(nextState.normalized())
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Stp116TopControls(
            state = state,
            onStateChange = { applyState(it) }
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(STP_116_STEP_COUNT) { index ->
                val step = state.steps.getOrNull(index) ?: Stp116StepState()
                Stp116StepRow(
                    stepIndex = index,
                    step = step,
                    onToggleEnabled = {
                        applyState(
                            state.updateStep(index) { current ->
                                current.copy(enabled = !current.enabled)
                            }.normalized()
                        )
                    },
                    onOpenDetail = { editingStepIndex = index },
                    onPitchClassChange = { newPitchClass ->
                        applyState(
                            state.updateStep(index) { current ->
                                current.copy(pitchClass = newPitchClass)
                            }.normalized()
                        )
                    },
                    onOctaveChange = { newOctave ->
                        applyState(
                            state.updateStep(index) { current ->
                                current.copy(octave = newOctave)
                            }.normalized()
                        )
                    },
                    onFineTuneChange = { newCents ->
                        applyState(
                            state.updateStep(index) { current ->
                                current.copy(fineTuneCents = newCents)
                            }.normalized()
                        )
                    }
                )
            }
        }
    }

    val openIndex = editingStepIndex
    if (openIndex != null) {
        val step = state.steps.getOrNull(openIndex) ?: Stp116StepState()
        Stp116StepDetailDialog(
            stepIndex = openIndex,
            step = step,
            onClose = { editingStepIndex = null },
            onVelocityChange = { value ->
                applyState(
                    state.updateStep(openIndex) { current ->
                        current.copy(velocity = value)
                    }.normalized()
                )
            },
            onGateDelayChange = { newDelay ->
                val maxGateLength = maxStp116GateLengthForDelay(newDelay)
                applyState(
                    state.updateStep(openIndex) { current ->
                        current.copy(
                            gateDelayTicks = newDelay,
                            gateLengthTicks = current.gateLengthTicks.coerceIn(
                                STP_116_MIN_GATE_LENGTH_TICKS,
                                maxGateLength
                            )
                        )
                    }.normalized()
                )
            },
            onGateLengthChange = { value ->
                applyState(
                    state.updateStep(openIndex) { current ->
                        current.copy(gateLengthTicks = value)
                    }.normalized()
                )
            }
        )
    }
}

@Composable
private fun Stp116TopControls(
    state: Stp116SequencerUiState,
    onStateChange: (Stp116SequencerUiState) -> Unit,
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProtoValueField(
                label = "CHNL",
                value = state.midiChannel.toString(),
                onDecrement = {
                    onStateChange(state.copy(midiChannel = state.midiChannel - 1).normalized())
                },
                onIncrement = {
                    onStateChange(state.copy(midiChannel = state.midiChannel + 1).normalized())
                },
                modifier = Modifier.weight(1f)
            )

            ProtoValueField(
                label = "LEN",
                value = state.sequenceLength.toString(),
                onDecrement = {
                    onStateChange(state.copy(sequenceLength = state.sequenceLength - 1).normalized())
                },
                onIncrement = {
                    onStateChange(state.copy(sequenceLength = state.sequenceLength + 1).normalized())
                },
                modifier = Modifier.weight(1f)
            )

            ProtoValueField(
                label = "CDIV",
                value = state.clockDivider.toString(),
                onDecrement = {
                    onStateChange(state.copy(clockDivider = state.clockDivider - 1).normalized())
                },
                onIncrement = {
                    onStateChange(state.copy(clockDivider = state.clockDivider + 1).normalized())
                },
                modifier = Modifier.weight(1f)
            )

            Box(modifier = Modifier.weight(1f)) {
                ProtoValueField(
                    label = "MODE",
                    value = state.playbackMode.shortLabel(),
                    onClick = { modeMenuExpanded = true }
                )

                DropdownMenu(
                    expanded = modeMenuExpanded,
                    onDismissRequest = { modeMenuExpanded = false }
                ) {
                    Stp116PlaybackMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.shortLabel()) },
                            onClick = {
                                modeMenuExpanded = false
                                onStateChange(state.copy(playbackMode = mode).normalized())
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
            PercentSlider(
                label = "DROP",
                value = state.bernoulliDropChance,
                onValueChange = { value ->
                    onStateChange(state.copy(bernoulliDropChance = value).normalized())
                },
                modifier = Modifier.weight(1f)
            )
            PercentSlider(
                label = "RGL",
                value = state.randomGateLengthAmount,
                onValueChange = { value ->
                    onStateChange(state.copy(randomGateLengthAmount = value).normalized())
                },
                modifier = Modifier.weight(1f)
            )
            PercentSlider(
                label = "RVEL",
                value = state.randomVelocityAmount,
                onValueChange = { value ->
                    onStateChange(state.copy(randomVelocityAmount = value).normalized())
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Stp116StepRow(
    stepIndex: Int,
    step: Stp116StepState,
    onToggleEnabled: () -> Unit,
    onOpenDetail: () -> Unit,
    onPitchClassChange: (Int) -> Unit,
    onOctaveChange: (Int) -> Unit,
    onFineTuneChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%02d".format(stepIndex + 1),
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )

        OutlinedButton(
            onClick = {},
            shape = ProtoControlShape,
            modifier = Modifier
                .height(36.dp)
                .combinedClickable(
                    onClick = onToggleEnabled,
                    onLongClick = onOpenDetail
                )
        ) {
            Text(if (step.enabled) "●" else "○")
        }

        CompactValueButton(
            label = "NOTE",
            value = STP_116_NOTE_NAMES[step.pitchClass.coerceIn(STP_116_MIN_PITCH_CLASS, STP_116_MAX_PITCH_CLASS)],
            onDecrement = {
                onPitchClassChange((step.pitchClass - 1).coerceAtLeast(STP_116_MIN_PITCH_CLASS))
            },
            onIncrement = {
                onPitchClassChange((step.pitchClass + 1).coerceAtMost(STP_116_MAX_PITCH_CLASS))
            },
            modifier = Modifier.weight(1f)
        )

        CompactValueButton(
            label = "OCT",
            value = step.octave.toString(),
            onDecrement = {
                onOctaveChange((step.octave - 1).coerceAtLeast(STP_116_MIN_OCTAVE))
            },
            onIncrement = {
                onOctaveChange((step.octave + 1).coerceAtMost(STP_116_MAX_OCTAVE))
            },
            modifier = Modifier.weight(1f)
        )

        CompactValueButton(
            label = "CENT",
            value = signedCentsText(step.fineTuneCents),
            onDecrement = {
                onFineTuneChange((step.fineTuneCents - 1).coerceAtLeast(STP_116_MIN_FINE_TUNE_CENTS))
            },
            onIncrement = {
                onFineTuneChange((step.fineTuneCents + 1).coerceAtMost(STP_116_MAX_FINE_TUNE_CENTS))
            },
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun Stp116StepDetailDialog(
    stepIndex: Int,
    step: Stp116StepState,
    onClose: () -> Unit,
    onVelocityChange: (Int) -> Unit,
    onGateDelayChange: (Int) -> Unit,
    onGateLengthChange: (Int) -> Unit,
) {
    val maxGateLength = maxStp116GateLengthForDelay(step.gateDelayTicks)

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = "STP 116 Step %02d".format(stepIndex + 1),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Velocity ${step.velocity}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = step.velocity.toFloat(),
                    onValueChange = { onVelocityChange(it.toInt()) },
                    valueRange = STP_116_MIN_VELOCITY.toFloat()..STP_116_MAX_VELOCITY.toFloat()
                )

                Text(
                    text = "Gate Delay ${step.gateDelayTicks}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = step.gateDelayTicks.toFloat(),
                    onValueChange = { onGateDelayChange(it.toInt()) },
                    valueRange = 0f..STP_116_MAX_GATE_DELAY_TICKS.toFloat()
                )

                Text(
                    text = "Gate Length ${step.gateLengthTicks}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = step.gateLengthTicks.toFloat(),
                    onValueChange = { onGateLengthChange(it.toInt()) },
                    valueRange = STP_116_MIN_GATE_LENGTH_TICKS.toFloat()..maxGateLength.toFloat()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun CompactValueButton(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = onDecrement,
                shape = ProtoControlShape,
                modifier = Modifier.height(32.dp)
            ) {
                Text("-")
            }
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = onIncrement,
                shape = ProtoControlShape,
                modifier = Modifier.height(32.dp)
            ) {
                Text("+")
            }
        }
    }
}

@Composable
private fun PercentSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
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
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(value.coerceIn(0f, 1f) * 100f).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { onValueChange(it.coerceIn(0f, 1f)) },
            valueRange = 0f..1f,
            modifier = Modifier.height(28.dp)
        )
    }
}

private fun signedCentsText(value: Int): String {
    return if (value >= 0) "+%02d".format(value) else value.toString()
}
