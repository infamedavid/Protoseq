package com.infamedavid.protoseq.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infamedavid.protoseq.ui.theme.ProtoSlider
import com.infamedavid.protoseq.ui.theme.ProtoTrack

@Composable
fun ProtoFader(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueText: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleHeightPx = remember(density) { with(density) { 26.dp.toPx() } }

    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val valueColor = MaterialTheme.colorScheme.secondary

    Column(
        modifier = modifier.wrapContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor
        )

        Canvas(
            modifier = Modifier
                .height(220.dp)
                .width(56.dp)
                .pointerInput(value) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (value - dragAmount / size.height).coerceIn(0f, 1f)
                            onValueChange(next)
                        }
                    )
                }
        ) {
            val trackWidth = size.width * 0.22f
            val trackX = (size.width - trackWidth) / 2f
            val handleWidth = size.width * 0.76f
            val handleX = (size.width - handleWidth) / 2f
            val y = (1f - value) * (size.height - handleHeightPx)

            drawRoundRect(
                color = ProtoTrack,
                topLeft = Offset(trackX, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(trackWidth, trackWidth)
            )

            repeat(9) { index ->
                val lineY = (index / 8f) * size.height
                drawLine(
                    color = tickColor,
                    start = Offset(0f, lineY),
                    end = Offset(size.width, lineY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            drawRoundRect(
                color = ProtoSlider,
                topLeft = Offset(handleX, y),
                size = Size(handleWidth, handleHeightPx),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
            )
        }

        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            textAlign = TextAlign.Center
        )
    }
}