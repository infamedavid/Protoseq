package com.infamedavid.protoseq.ui.sequencers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infamedavid.protoseq.features.stochastic.StochasticSequencerUiState
import com.infamedavid.protoseq.ui.components.ProtoDualSliderRow

@Composable
fun TuringMachinePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun TuringMachineMainControls(
    state: StochasticSequencerUiState,
    onLockPositionChange: (Float) -> Unit,
    onSequenceLengthChange: (Int) -> Unit,
    onSlewAmountChange: (Float) -> Unit,
    onBernoulliProbabilityChange: (Float) -> Unit,
    onPitchRangeSemitonesChange: (Int) -> Unit,
    onPitchOffsetChange: (Int) -> Unit,
    onGateLengthChange: (Float) -> Unit,
    onRandomGateLengthChange: (Float) -> Unit,
) {
    TuringMachineMainControlsContent(
        state = state,
        onLockPositionChange = onLockPositionChange,
        onSequenceLengthChange = onSequenceLengthChange,
        onSlewAmountChange = onSlewAmountChange,
        onBernoulliProbabilityChange = onBernoulliProbabilityChange,
        onPitchRangeSemitonesChange = onPitchRangeSemitonesChange,
        onPitchOffsetChange = onPitchOffsetChange,
        onGateLengthChange = onGateLengthChange,
        onRandomGateLengthChange = onRandomGateLengthChange
    )
}

@Composable
private fun TuringMachineMainControlsContent(
    state: StochasticSequencerUiState,
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
        leftValue = state.lockPosition,
        leftValueText = "${(state.lockPosition * 100).toInt()}%",
        onLeftValueChange = onLockPositionChange,
        leftValueRange = -1f..1f,
        rightLabel = "SQLN",
        rightValue = (state.sequenceLength - 2) / 62f,
        rightValueText = state.sequenceLength.toString(),
        onRightValueChange = { normalized ->
            onSequenceLengthChange((2 + normalized * 62).toInt())
        }
    )

    ProtoDualSliderRow(
        leftLabel = "SLEW",
        leftValue = state.slewAmount,
        leftValueText = "${(state.slewAmount * 100).toInt()}%",
        onLeftValueChange = onSlewAmountChange,
        rightLabel = "BRNL",
        rightValue = state.bernoulliProbability,
        rightValueText = "${(state.bernoulliProbability * 100).toInt()}%",
        onRightValueChange = onBernoulliProbabilityChange
    )

    val rangeSemitones = state.pitchRangeSemitones
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
            onPitchRangeSemitonesChange((1 + normalized * 63).toInt())
        },
        rightLabel = "OFST",
        rightValue = (state.pitchOffset + 24) / 48f,
        rightValueText = state.pitchOffset.toString(),
        onRightValueChange = { normalized ->
            onPitchOffsetChange((-24 + normalized * 48).toInt())
        }
    )

    ProtoDualSliderRow(
        leftLabel = "GLEN",
        leftValue = state.gateLength,
        leftValueText = "${(state.gateLength * 100).toInt()}%",
        onLeftValueChange = onGateLengthChange,
        rightLabel = "RLEN",
        rightValue = state.randomGateLength,
        rightValueText = "${(state.randomGateLength * 100).toInt()}%",
        onRightValueChange = onRandomGateLengthChange
    )
}
