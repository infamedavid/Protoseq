package com.infamedavid.protoseq.ui.sequencers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infamedavid.protoseq.features.stochastic.MidiOutputMode
import com.infamedavid.protoseq.ui.components.ProtoControlShape
import com.infamedavid.protoseq.ui.components.ProtoDualSliderRow
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
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
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
        content()
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
