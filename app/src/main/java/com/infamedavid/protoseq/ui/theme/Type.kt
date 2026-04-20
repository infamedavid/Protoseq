package com.infamedavid.protoseq.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.infamedavid.protoseq.R

private val Rajdhani = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold)
)

val ProtoseqTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Bold),
    displayMedium = Typography().displayMedium.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Bold),
    displaySmall = Typography().displaySmall.copy(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Bold),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold),
    titleSmall = Typography().titleSmall.copy(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Medium),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Medium),
    bodySmall = Typography().bodySmall.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Medium),
    labelLarge = Typography().labelLarge.copy(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold),
    labelMedium = Typography().labelMedium.copy(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold),
    labelSmall = Typography().labelSmall.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Medium)
)
