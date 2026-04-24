package com.infamedavid.protoseq.ui.sequencers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infamedavid.protoseq.features.stochastic.MidiOutputMode
import com.infamedavid.protoseq.core.repeater.RptrDivision
import com.infamedavid.protoseq.core.repeater.RptrStartMode
import com.infamedavid.protoseq.ui.components.ProtoControlShape
import com.infamedavid.protoseq.ui.components.ProtoDualSliderRow
import com.infamedavid.protoseq.ui.components.ProtoMomentaryButton
import com.infamedavid.protoseq.ui.components.ProtoValueField

@Composable
fun TuringMachinePanel(
    lockPosition: Float,
    sequenceLength: Int,
    slewAmount: Float,
    bernoulliProbability: Float,
    pitchRangeSemitones: Int,
    pitchOffset: Int,
    gateLength: Float,
    randomGateLength: Float,
    onLockPositionChange: (Float) -> Unit,
    onSequenceLengthChange: (Int) -> Unit,
    onSlewAmountChange: (Float) -> Unit,
    onBernoulliProbabilityChange: (Float) -> Unit,
    onPitchRangeSemitonesChange: (Int) -> Unit,
    onPitchOffsetChange: (Int) -> Unit,
    onGateLengthChange: (Float) -> Unit,
    onRandomGateLengthChange: (Float) -> Unit,
    outputMode: MidiOutputMode,
    midiChannel: Int,
    baseNoteDisplay: String,
    quantizationDisplayName: String,
    ccNumber: Int,
    onOutputModeChange: (MidiOutputMode) -> Unit,
    onDecrementMidiChannel: () -> Unit,
    onIncrementMidiChannel: () -> Unit,
    onDecrementBaseNote: () -> Unit,
    onIncrementBaseNote: () -> Unit,
    onQuantizationClick: () -> Unit,
    onDecrementCcNumber: () -> Unit,
    onIncrementCcNumber: () -> Unit,
    rptrIsRuntimeActive: Boolean,
    activeRptrDivision: RptrDivision?,
    rptrBaseUnits: Int,
    rptrStartMode: RptrStartMode,
    showRptrBasePickerDialog: Boolean,
    onShowRptrBasePickerDialogChange: (Boolean) -> Unit,
    onDecrementRptrBaseUnits: () -> Unit,
    onIncrementRptrBaseUnits: () -> Unit,
    onSetRptrBaseUnits: (Int) -> Unit,
    onSetRptrStartMode: (RptrStartMode) -> Unit,
    onPressRptr: (RptrDivision) -> Unit,
    onReleaseRptr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TuringMachineMainControls(
            lockPosition = lockPosition,
            sequenceLength = sequenceLength,
            slewAmount = slewAmount,
            bernoulliProbability = bernoulliProbability,
            pitchRangeSemitones = pitchRangeSemitones,
            pitchOffset = pitchOffset,
            gateLength = gateLength,
            randomGateLength = randomGateLength,
            onLockPositionChange = onLockPositionChange,
            onSequenceLengthChange = onSequenceLengthChange,
            onSlewAmountChange = onSlewAmountChange,
            onBernoulliProbabilityChange = onBernoulliProbabilityChange,
            onPitchRangeSemitonesChange = onPitchRangeSemitonesChange,
            onPitchOffsetChange = onPitchOffsetChange,
            onGateLengthChange = onGateLengthChange,
            onRandomGateLengthChange = onRandomGateLengthChange
        )
        TuringMachineMidiControls(
            outputMode = outputMode,
            midiChannel = midiChannel,
            baseNoteDisplay = baseNoteDisplay,
            quantizationDisplayName = quantizationDisplayName,
            ccNumber = ccNumber,
            onOutputModeChange = onOutputModeChange,
            onDecrementMidiChannel = onDecrementMidiChannel,
            onIncrementMidiChannel = onIncrementMidiChannel,
            onDecrementBaseNote = onDecrementBaseNote,
            onIncrementBaseNote = onIncrementBaseNote,
            onQuantizationClick = onQuantizationClick,
            onDecrementCcNumber = onDecrementCcNumber,
            onIncrementCcNumber = onIncrementCcNumber
        )
        TuringMachineRepeaterControls(
            outputMode = outputMode,
            rptrIsRuntimeActive = rptrIsRuntimeActive,
            activeRptrDivision = activeRptrDivision,
            rptrBaseUnits = rptrBaseUnits,
            rptrStartMode = rptrStartMode,
            showRptrBasePickerDialog = showRptrBasePickerDialog,
            onShowRptrBasePickerDialogChange = onShowRptrBasePickerDialogChange,
            onDecrementRptrBaseUnits = onDecrementRptrBaseUnits,
            onIncrementRptrBaseUnits = onIncrementRptrBaseUnits,
            onSetRptrBaseUnits = onSetRptrBaseUnits,
            onSetRptrStartMode = onSetRptrStartMode,
            onPressRptr = onPressRptr,
            onReleaseRptr = onReleaseRptr
        )
    }
}

@Composable
private fun TuringMachineMainControls(
    lockPosition: Float,
    sequenceLength: Int,
    slewAmount: Float,
    bernoulliProbability: Float,
    pitchRangeSemitones: Int,
    pitchOffset: Int,
    gateLength: Float,
    randomGateLength: Float,
    onLockPositionChange: (Float) -> Unit,
    onSequenceLengthChange: (Int) -> Unit,
    onSlewAmountChange: (Float) -> Unit,
    onBernoulliProbabilityChange: (Float) -> Unit,
    onPitchRangeSemitonesChange: (Int) -> Unit,
    onPitchOffsetChange: (Int) -> Unit,
    onGateLengthChange: (Float) -> Unit,
    onRandomGateLengthChange: (Float) -> Unit,
) {
    ProtoDualSliderRow(
        leftLabel = "LOCK",
        leftValue = lockPosition,
        leftValueText = "${(lockPosition * 100).toInt()}%",
        onLeftValueChange = onLockPositionChange,
        leftValueRange = -1f..1f,
        rightLabel = "SQLN",
        rightValue = (sequenceLength - 2) / 62f,
        rightValueText = sequenceLength.toString(),
        onRightValueChange = { normalized ->
            onSequenceLengthChange((2 + normalized * 62).toInt())
        }
    )

    ProtoDualSliderRow(
        leftLabel = "SLEW",
        leftValue = slewAmount,
        leftValueText = "${(slewAmount * 100).toInt()}%",
        onLeftValueChange = onSlewAmountChange,
        rightLabel = "BRNL",
        rightValue = bernoulliProbability,
        rightValueText = "${(bernoulliProbability * 100).toInt()}%",
        onRightValueChange = onBernoulliProbabilityChange
    )

    val rangeOctaves = pitchRangeSemitones / 12
    val rangeRemainder = pitchRangeSemitones % 12
    val rangeDisplay = if (rangeRemainder == 0) {
        "$rangeOctaves OCT"
    } else {
        "$rangeOctaves OCT + $rangeRemainder"
    }

    ProtoDualSliderRow(
        leftLabel = "RANG",
        leftValue = (pitchRangeSemitones - 1) / 63f,
        leftValueText = rangeDisplay,
        onLeftValueChange = { normalized ->
            onPitchRangeSemitonesChange((1 + normalized * 63).toInt())
        },
        rightLabel = "OFST",
        rightValue = (pitchOffset + 24) / 48f,
        rightValueText = pitchOffset.toString(),
        onRightValueChange = { normalized ->
            onPitchOffsetChange((-24 + normalized * 48).toInt())
        }
    )

    ProtoDualSliderRow(
        leftLabel = "GLEN",
        leftValue = gateLength,
        leftValueText = "${(gateLength * 100).toInt()}%",
        onLeftValueChange = onGateLengthChange,
        rightLabel = "RLEN",
        rightValue = randomGateLength,
        rightValueText = "${(randomGateLength * 100).toInt()}%",
        onRightValueChange = onRandomGateLengthChange
    )
}

@Composable
private fun TuringMachineMidiControls(
    outputMode: MidiOutputMode,
    midiChannel: Int,
    baseNoteDisplay: String,
    quantizationDisplayName: String,
    ccNumber: Int,
    onOutputModeChange: (MidiOutputMode) -> Unit,
    onDecrementMidiChannel: () -> Unit,
    onIncrementMidiChannel: () -> Unit,
    onDecrementBaseNote: () -> Unit,
    onIncrementBaseNote: () -> Unit,
    onQuantizationClick: () -> Unit,
    onDecrementCcNumber: () -> Unit,
    onIncrementCcNumber: () -> Unit,
) {
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
                    onClick = { onOutputModeChange(MidiOutputMode.NOTE) },
                    modifier = Modifier.weight(1f),
                    enabled = outputMode != MidiOutputMode.NOTE,
                    shape = ProtoControlShape
                ) {
                    Text(text = "N")
                }

                OutlinedButton(
                    onClick = { onOutputModeChange(MidiOutputMode.CC) },
                    modifier = Modifier.weight(1f),
                    enabled = outputMode != MidiOutputMode.CC,
                    shape = ProtoControlShape
                ) {
                    Text(text = "C")
                }
            }
        }

        ProtoValueField(
            label = "CHNL",
            value = midiChannel.toString(),
            modifier = Modifier.weight(1f),
            onDecrement = onDecrementMidiChannel,
            onIncrement = onIncrementMidiChannel
        )

        ProtoValueField(
            label = "BASE",
            value = baseNoteDisplay,
            modifier = Modifier.weight(1f),
            onDecrement = onDecrementBaseNote,
            onIncrement = onIncrementBaseNote
        )

        ProtoValueField(
            label = "QUAN",
            value = quantizationDisplayName,
            modifier = Modifier.weight(1f),
            onClick = onQuantizationClick
        )

        if (outputMode == MidiOutputMode.CC) {
            ProtoValueField(
                label = "CC#",
                value = ccNumber.toString(),
                modifier = Modifier.weight(1f),
                onDecrement = onDecrementCcNumber,
                onIncrement = onIncrementCcNumber
            )
        }
    }
}

@Composable
private fun TuringMachineRepeaterControls(
    outputMode: MidiOutputMode,
    rptrIsRuntimeActive: Boolean,
    activeRptrDivision: RptrDivision?,
    rptrBaseUnits: Int,
    rptrStartMode: RptrStartMode,
    showRptrBasePickerDialog: Boolean,
    onShowRptrBasePickerDialogChange: (Boolean) -> Unit,
    onDecrementRptrBaseUnits: () -> Unit,
    onIncrementRptrBaseUnits: () -> Unit,
    onSetRptrBaseUnits: (Int) -> Unit,
    onSetRptrStartMode: (RptrStartMode) -> Unit,
    onPressRptr: (RptrDivision) -> Unit,
    onReleaseRptr: () -> Unit,
) {
    val rptrBlockEnabled = outputMode != MidiOutputMode.CC
    val rptrConfigControlsEnabled = rptrBlockEnabled && !rptrIsRuntimeActive
    val rptrDivisionButtonsEnabled = rptrBlockEnabled
    val rptrBaseQuickPickValues = listOf(
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17,
        19, 21, 23, 29, 31, 32, 37, 41, 43, 47, 53, 59, 61, 64
    )

    Spacer(modifier = Modifier.height(2.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "BASE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDecrementRptrBaseUnits,
                    modifier = Modifier
                        .weight(0.8f)
                        .height(48.dp),
                    enabled = rptrConfigControlsEnabled,
                    shape = ProtoControlShape
                ) {
                    Text(text = "-")
                }

                OutlinedButton(
                    onClick = { onShowRptrBasePickerDialogChange(true) },
                    modifier = Modifier
                        .weight(2f)
                        .height(48.dp),
                    enabled = rptrConfigControlsEnabled,
                    shape = ProtoControlShape
                ) {
                    Text(
                        text = rptrBaseUnits.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                OutlinedButton(
                    onClick = onIncrementRptrBaseUnits,
                    modifier = Modifier
                        .weight(0.8f)
                        .height(48.dp),
                    enabled = rptrConfigControlsEnabled,
                    shape = ProtoControlShape
                ) {
                    Text(text = "+")
                }
            }
        }

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
                    onClick = { onSetRptrStartMode(RptrStartMode.FREE) },
                    modifier = Modifier.weight(1f),
                    enabled = rptrConfigControlsEnabled && rptrStartMode != RptrStartMode.FREE,
                    shape = ProtoControlShape
                ) {
                    Text(text = "FREE")
                }

                OutlinedButton(
                    onClick = { onSetRptrStartMode(RptrStartMode.GRID) },
                    modifier = Modifier.weight(1f),
                    enabled = rptrConfigControlsEnabled && rptrStartMode != RptrStartMode.GRID,
                    shape = ProtoControlShape
                ) {
                    Text(text = "GRID")
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val divisions = listOf(
            "1/2" to RptrDivision.D2,
            "1/4" to RptrDivision.D4,
            "1/8" to RptrDivision.D8,
            "1/16" to RptrDivision.D16,
            "1/32" to RptrDivision.D32
        )

        divisions.forEach { (label, division) ->
            ProtoMomentaryButton(
                label = label,
                isActive = activeRptrDivision == division,
                onPress = { onPressRptr(division) },
                onRelease = onReleaseRptr,
                modifier = Modifier.weight(1f),
                enabled = rptrDivisionButtonsEnabled
            )
        }
    }

    if (showRptrBasePickerDialog) {
        AlertDialog(
            onDismissRequest = { onShowRptrBasePickerDialogChange(false) },
            title = { Text(text = "Select BASE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rptrBaseQuickPickValues.chunked(5).forEach { rowValues ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowValues.forEach { value ->
                                OutlinedButton(
                                    onClick = {
                                        onSetRptrBaseUnits(value)
                                        onShowRptrBasePickerDialogChange(false)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = ProtoControlShape
                                ) {
                                    val prefix = if (value == rptrBaseUnits) "✓ " else ""
                                    Text(text = "$prefix$value")
                                }
                            }
                            repeat(5 - rowValues.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onShowRptrBasePickerDialogChange(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}
