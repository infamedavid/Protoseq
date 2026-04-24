package com.infamedavid.protoseq.ui.sequencers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infamedavid.protoseq.ui.components.ProtoDualSliderRow

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
