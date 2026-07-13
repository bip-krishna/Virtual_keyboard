package com.visionboard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DefaultFontFamily = FontFamily.Default

private val MonospaceFontFamily = FontFamily.Monospace

val VisionBoardTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    headlineLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    titleLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    bodyLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    labelLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// ─── Developer Mode — Monospace Typography ─────────────────────────────────────

val DeveloperModeTypography = Typography(
    displayLarge = VisionBoardTypography.displayLarge.copy(
        fontFamily = MonospaceFontFamily
    ),
    displayMedium = VisionBoardTypography.displayMedium.copy(
        fontFamily = MonospaceFontFamily
    ),
    displaySmall = VisionBoardTypography.displaySmall.copy(
        fontFamily = MonospaceFontFamily
    ),
    headlineLarge = VisionBoardTypography.headlineLarge.copy(
        fontFamily = MonospaceFontFamily
    ),
    headlineMedium = VisionBoardTypography.headlineMedium.copy(
        fontFamily = MonospaceFontFamily
    ),
    headlineSmall = VisionBoardTypography.headlineSmall.copy(
        fontFamily = MonospaceFontFamily
    ),
    titleLarge = VisionBoardTypography.titleLarge.copy(
        fontFamily = MonospaceFontFamily
    ),
    titleMedium = VisionBoardTypography.titleMedium.copy(
        fontFamily = MonospaceFontFamily
    ),
    titleSmall = VisionBoardTypography.titleSmall.copy(
        fontFamily = MonospaceFontFamily
    ),
    bodyLarge = VisionBoardTypography.bodyLarge.copy(
        fontFamily = MonospaceFontFamily
    ),
    bodyMedium = VisionBoardTypography.bodyMedium.copy(
        fontFamily = MonospaceFontFamily
    ),
    bodySmall = VisionBoardTypography.bodySmall.copy(
        fontFamily = MonospaceFontFamily
    ),
    labelLarge = VisionBoardTypography.labelLarge.copy(
        fontFamily = MonospaceFontFamily
    ),
    labelMedium = VisionBoardTypography.labelMedium.copy(
        fontFamily = MonospaceFontFamily
    ),
    labelSmall = VisionBoardTypography.labelSmall.copy(
        fontFamily = MonospaceFontFamily
    )
)
