package com.infamedavid.protoseq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infamedavid.protoseq.features.stochastic.MidiOutputMode
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerViewModel
import com.infamedavid.protoseq.features.transport.TransportViewModel
import com.infamedavid.protoseq.ui.components.ModuleCard
import com.infamedavid.protoseq.ui.components.ProtoButton
import com.infamedavid.protoseq.ui.components.ProtoDualSliderRow
import com.infamedavid.protoseq.ui.components.ProtoSliderRow
import com.infamedavid.protoseq.ui.components.ProtoValueField
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModuleCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProtoSliderRow(
                        label = "BPM ",
                        value = (transportState.bpm - 40f) / 200f,
                        valueText = transportState.bpm.toInt().toString(),
                        onValueChange = { normalized ->
                            transportViewModel.setBpm(40f + (normalized * 200f))
                        }
                    )

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

                    val selectedMidiTarget = transportState.midiOutputTargets.firstOrNull {
                        it.selectionId == transportState.selectedMidiOutputId
                    }

                    Text(
                        text = selectedMidiTarget?.let {
                            "MIDI OUT: ${it.name} (IN ${it.inputPortNumber + 1})"
                        } ?: "No MIDI output selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

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
                                    Text(text = "${target.name} IN${target.inputPortNumber + 1}")
                                }
                            }
                        }
                    }
                }
            }

            ModuleCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProtoDualSliderRow(
                        leftLabel = "LOCK",
                        leftValue = stochasticState.lockPosition,
                        leftValueText = "${(stochasticState.lockPosition * 100).toInt()}%",
                        onLeftValueChange = stochasticViewModel::setLockPosition,
                        leftValueRange = -1f..1f,
                        rightLabel = "SQLN",
                        rightValue = (stochasticState.sequenceLength - 8) / 24f,
                        rightValueText = stochasticState.sequenceLength.toString(),
                        onRightValueChange = { normalized ->
                            stochasticViewModel.setSequenceLength((8 + normalized * 24).toInt())
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

                    ProtoDualSliderRow(
                        leftLabel = "RANG",
                        leftValue = (stochasticState.pitchRangeOctaves - 1) / 4f,
                        leftValueText = "${stochasticState.pitchRangeOctaves} OCT",
                        onLeftValueChange = { normalized ->
                            stochasticViewModel.setPitchRangeOctaves((1 + normalized * 4).toInt())
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
                                    Text(text = "NOTE")
                                }

                                OutlinedButton(
                                    onClick = { stochasticViewModel.setOutputMode(MidiOutputMode.CC) },
                                    modifier = Modifier.weight(1f),
                                    enabled = stochasticState.outputMode != MidiOutputMode.CC
                                ) {
                                    Text(text = "CC")
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
            }

            ModuleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
