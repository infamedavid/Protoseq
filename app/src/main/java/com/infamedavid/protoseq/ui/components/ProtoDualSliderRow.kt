package com.infamedavid.protoseq.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProtoDualSliderRow(
    leftLabel: String,
    leftValue: Float,
    leftValueText: String,
    onLeftValueChange: (Float) -> Unit,
    rightLabel: String,
    rightValue: Float,
    rightValueText: String,
    onRightValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProtoSliderRow(
            label = leftLabel,
            value = leftValue,
            valueText = leftValueText,
            onValueChange = onLeftValueChange,
            modifier = Modifier.weight(1f)
        )

        ProtoSliderRow(
            label = rightLabel,
            value = rightValue,
            valueText = rightValueText,
            onValueChange = onRightValueChange,
            modifier = Modifier.weight(1f)
        )
    }
}