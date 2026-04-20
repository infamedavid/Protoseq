package com.infamedavid.protoseq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infamedavid.protoseq.features.stochastic.MidiOutputMode
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerViewModel
import com.infamedavid.protoseq.features.stochastic.toConfig
import com.infamedavid.protoseq.features.transport.TransportViewModel
import com.infamedavid.protoseq.ui.components.ProtoButton
import com.infamedavid.protoseq.ui.components.ProtoDualSliderRow
import com.infamedavid.protoseq.ui.components.ProtoValueField
import com.infamedavid.protoseq.ui.util.buildMidiTargetShortLabels
import com.infamedavid.protoseq.ui.util.midiNoteToDisplay

@Composable
fun AppScreen(
    stochasticViewModel: StochasticSequencerViewModel = viewModel()
) {
    val context = LocalContext.current
    val transportViewModel: TransportViewModel = viewModel(
        factory = TransportViewModel.factory(context)
    )
    val transportState by transportViewModel.uiState.collectAsState()
    val stochasticState by stochasticViewModel.uiState.collectAsState()

    var showBpmInputDialog by rememberSaveable { mutableStateOf(false) }
    var bpmInputText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(stochasticState) {
        transportViewModel.updateSequencerConfig(stochasticState.toConfig())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Slider(
                    value = ((transportState.bpm - 1f) / 299f).coerceIn(0f, 1f),
                    onValueChange = { normalized ->
                        transportViewModel.setBpm(1f + (normalized * 299f))
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { transportViewModel.setBpm(transportState.bpm - 1f) }
                    ) {
                        Text(text = "-")
                    }

                    Text(
                        text = "${transportState.bpm.toInt()} BPM",
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                bpmInputText = transportState.bpm.toInt().toString()
                                showBpmInputDialog = true
                            }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedButton(
                        onClick = { transportViewModel.setBpm(transportState.bpm + 1f) }
                    ) {
                        Text(text = "+")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProtoButton(
                        label = "PLAY",
                        modifier = Modifier.weight(1f)
                    ) {
                        transportViewModel.play()
                    }

                    ProtoButton(
                        label = "STOP",
                        modifier = Modifier.weight(1f)
                    ) {
                        transportViewModel.stop()
                    }

                    ProtoButton(
                        label = "PAUS",
                        modifier = Modifier.weight(1f)
                    ) {
                        transportViewModel.pause()
                    }
                }

                val midiTargetLabels = remember(transportState.midiOutputTargets) {
                    buildMidiTargetShortLabels(transportState.midiOutputTargets)
                }

                if (transportState.midiOutputTargets.isEmpty()) {
                    Text(
                        text = "No MIDI output targets available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { transportViewModel.clearMidiOutputSelection() },
                            enabled = transportState.selectedMidiOutputId != null
                        ) {
                            Text(text = "NONE")
                        }

                        transportState.midiOutputTargets.forEach { target ->
                            OutlinedButton(
                                onClick = {
                                    transportViewModel.selectMidiOutputTarget(target.selectionId)
                                },
                                enabled = transportState.selectedMidiOutputId != target.selectionId
                            ) {
                                Text(text = midiTargetLabels[target.selectionId] ?: "OUT")
                            }
                        }
                    }
                }
            }
            SectionDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProtoDualSliderRow(
                    leftLabel = "LOCK",
                    leftValue = stochasticState.lockPosition,
                    leftValueText = "${(stochasticState.lockPosition * 100).toInt()}%",
                    onLeftValueChange = stochasticViewModel::setLockPosition,
                    leftValueRange = -1f..1f,
                    rightLabel = "SQLN",
                    rightValue = (stochasticState.sequenceLength - 2) / 62f,
                    rightValueText = stochasticState.sequenceLength.toString(),
                    onRightValueChange = { normalized ->
                        stochasticViewModel.setSequenceLength((2 + normalized * 62).toInt())
                    }
                )

                ProtoDualSliderRow(
                    leftLabel = "SLEW",
                    leftValue = stochasticState.slewAmount,
                    leftValueText = "${(stochasticState.slewAmount * 100).toInt()}%",
                    onLeftValueChange = stochasticViewModel::setSlewAmount,
                    rightLabel = "BRNL",
                    rightValue = stochasticState.bernoulliProbability,
                    rightValueText = "${(stochasticState.bernoulliProbability * 100).toInt()}%",
                    onRightValueChange = stochasticViewModel::setBernoulliProbability
                )

                val rangeSemitones = stochasticState.pitchRangeSemitones
                val rangeOctaves = rangeSemitones / 12
                val rangeRemainder = rangeSemitones % 12
                val rangeDisplay = if (rangeRemainder == 0) {
                    "$rangeOctaves OCT"
                } else {
                    "$rangeOctaves OCT + $rangeRemainder"
                }

                ProtoDualSliderRow(
                    leftLabel = "RANG",
                    leftValue = (rangeSemitones - 1) / 63f,
                    leftValueText = rangeDisplay,
                    onLeftValueChange = { normalized ->
                        stochasticViewModel.setPitchRangeSemitones((1 + normalized * 63).toInt())
                    },
                    rightLabel = "OFST",
                    rightValue = (stochasticState.pitchOffset + 24) / 48f,
                    rightValueText = stochasticState.pitchOffset.toString(),
                    onRightValueChange = { normalized ->
                        stochasticViewModel.setPitchOffset((-24 + normalized * 48).toInt())
                    }
                )

                ProtoDualSliderRow(
                    leftLabel = "GLEN",
                    leftValue = stochasticState.gateLength,
                    leftValueText = "${(stochasticState.gateLength * 100).toInt()}%",
                    onLeftValueChange = stochasticViewModel::setGateLength,
                    rightLabel = "RLEN",
                    rightValue = stochasticState.randomGateLength,
                    rightValueText = "${(stochasticState.randomGateLength * 100).toInt()}%",
                    onRightValueChange = stochasticViewModel::setRandomGateLength
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "MODE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { stochasticViewModel.setOutputMode(MidiOutputMode.NOTE) },
                                modifier = Modifier.weight(1f),
                                enabled = stochasticState.outputMode != MidiOutputMode.NOTE
                            ) {
                                Text(text = "N")
                            }

                            OutlinedButton(
                                onClick = { stochasticViewModel.setOutputMode(MidiOutputMode.CC) },
                                modifier = Modifier.weight(1f),
                                enabled = stochasticState.outputMode != MidiOutputMode.CC
                            ) {
                                Text(text = "C")
                            }
                        }
                    }

                    ProtoValueField(
                        label = "CHNL",
                        value = stochasticState.midiChannel.toString(),
                        modifier = Modifier.weight(1f),
                        onDecrement = stochasticViewModel::decrementMidiChannel,
                        onIncrement = stochasticViewModel::incrementMidiChannel
                    )

                    ProtoValueField(
                        label = "BASE",
                        value = midiNoteToDisplay(stochasticState.baseNote),
                        modifier = Modifier.weight(1f),
                        onDecrement = stochasticViewModel::decrementBaseNote,
                        onIncrement = stochasticViewModel::incrementBaseNote
                    )

                    ProtoValueField(
                        label = "QUAN",
                        value = stochasticState.quantizationMode.displayName,
                        modifier = Modifier.weight(1f),
                        onDecrement = stochasticViewModel::previousQuantizationMode,
                        onIncrement = stochasticViewModel::nextQuantizationMode
                    )

                    if (stochasticState.outputMode == MidiOutputMode.CC) {
                        ProtoValueField(
                            label = "CC#",
                            value = stochasticState.ccNumber.toString(),
                            modifier = Modifier.weight(1f),
                            onDecrement = {
                                stochasticViewModel.setCcNumber(stochasticState.ccNumber - 1)
                            },
                            onIncrement = {
                                stochasticViewModel.setCcNumber(stochasticState.ccNumber + 1)
                            }
                        )
                    }
                }
            }
            SectionDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CH ${stochasticState.midiChannel}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "NOTE ${midiNoteToDisplay(stochasticState.baseNote)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "GATE ON",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "TRIG YES",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "LEN ${(stochasticState.gateLength * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        if (showBpmInputDialog) {
            AlertDialog(
                onDismissRequest = { showBpmInputDialog = false },
                title = { Text(text = "Set BPM") },
                text = {
                    OutlinedTextField(
                        value = bpmInputText,
                        onValueChange = { input: String ->
                            bpmInputText = input.filter { ch -> ch.isDigit() }
                        },
                        singleLine = true,
                        label = { Text("BPM (1-300)") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            bpmInputText
                                .toIntOrNull()
                                ?.coerceIn(1, 300)
                                ?.toFloat()
                                ?.let(transportViewModel::setBpm)
                            showBpmInputDialog = false
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBpmInputDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    )
}
