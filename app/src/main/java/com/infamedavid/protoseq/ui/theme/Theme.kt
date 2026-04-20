package com.infamedavid.protoseq.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ProtoseqColors = lightColorScheme(
    background = ProtoBackground,
    surface = ProtoPanel,
    surfaceVariant = ProtoPanelAlt,
    primary = ProtoAccent,
    secondary = ProtoAccentSoft,
    onBackground = ProtoText,
    onSurface = ProtoText,
    onPrimary = ProtoPanel
)

@Composable
fun ProtoseqTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ProtoseqColors,
        typography = ProtoseqTypography,
        content = content
    )
}