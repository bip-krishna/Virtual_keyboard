package com.visionboard.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Primary Palette ──────────────────────────────────────────────────────────
val DeepBlue = Color(0xFF1565C0)
val DeepBlueLight = Color(0xFF1E88E5)
val DeepBlueDark = Color(0xFF0D47A1)

val Teal = Color(0xFF00897B)
val TealLight = Color(0xFF26A69A)
val TealDark = Color(0xFF00695C)

val Amber = Color(0xFFFF8F00)
val AmberLight = Color(0xFFFFA000)
val AmberDark = Color(0xFFFF6F00)

// ─── Surface & Background ─────────────────────────────────────────────────────
val SurfaceDark = Color(0xFF0D1117)
val SurfaceDarkVariant = Color(0xFF161B22)
val SurfaceDarkContainer = Color(0xFF1C2129)

val SurfaceLight = Color(0xFFFAFBFC)
val SurfaceLightVariant = Color(0xFFF0F2F5)
val SurfaceLightContainer = Color(0xFFE8EAED)

// ─── Error ────────────────────────────────────────────────────────────────────
val ErrorRed = Color(0xFFD32F2F)
val ErrorRedLight = Color(0xFFEF5350)
val ErrorRedDark = Color(0xFFC62828)

// ─── On Colors ────────────────────────────────────────────────────────────────
val OnSurfaceDark = Color(0xFFE6EDF3)
val OnSurfaceLight = Color(0xFF1F2328)

val OnPrimaryDark = Color(0xFFFFFFFF)
val OnPrimaryLight = Color(0xFFFFFFFF)

// ─── Semantic — VisionBoard ───────────────────────────────────────────────────
val KeyboardHighlight = Color(0xFF42A5F5)
val KeyPress = Color(0xFF66BB6A)
val FingerPosition = Color(0xFFFF7043)
val VelocityVector = Color(0xFFAB47BC)
val HomographyGrid = Color(0xFF78909C)
val DetectedKey = Color(0xFFEC407A)

// ─── Dark Theme Colors ────────────────────────────────────────────────────────
val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = DeepBlueLight,
    onPrimary = OnPrimaryDark,
    primaryContainer = DeepBlueDark,
    onPrimaryContainer = Color(0xFFD1E4FF),

    secondary = TealLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = TealDark,
    onSecondaryContainer = Color(0xFFB2DFDB),

    tertiary = AmberLight,
    onTertiary = Color(0xFF000000),
    tertiaryContainer = AmberDark,
    onTertiaryContainer = Color(0xFFFFECB3),

    background = SurfaceDark,
    onBackground = OnSurfaceDark,

    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceDarkVariant,
    onSurfaceVariant = Color(0xFF8B949E),

    surfaceContainerLow = SurfaceDarkVariant,
    surfaceContainer = SurfaceDarkContainer,
    surfaceContainerHigh = Color(0xFF21262D),
    surfaceContainerHighest = Color(0xFF2D333B),

    error = ErrorRedLight,
    onError = Color(0xFF000000),
    errorContainer = ErrorRedDark,
    onErrorContainer = Color(0xFFFFCDD2),

    outline = Color(0xFF484F58),
    outlineVariant = Color(0xFF30363D),

    inverseSurface = OnSurfaceDark,
    inverseOnSurface = SurfaceDark,
    inversePrimary = DeepBlueDark,

    scrim = Color(0xFF000000)
)

// ─── Light Theme Colors ───────────────────────────────────────────────────────
val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = DeepBlue,
    onPrimary = OnPrimaryLight,
    primaryContainer = DeepBlueLight,
    onPrimaryContainer = DeepBlueDark,

    secondary = Teal,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = TealLight,
    onSecondaryContainer = TealDark,

    tertiary = Amber,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = AmberLight,
    onTertiaryContainer = AmberDark,

    background = SurfaceLight,
    onBackground = OnSurfaceLight,

    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceLightVariant,
    onSurfaceVariant = Color(0xFF576069),

    surfaceContainerLow = SurfaceLightVariant,
    surfaceContainer = SurfaceLightContainer,
    surfaceContainerHigh = Color(0xFFD0D4DA),
    surfaceContainerHighest = Color(0xFFBCC1C7),

    error = ErrorRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = ErrorRedLight,
    onErrorContainer = ErrorRedDark,

    outline = Color(0xFF8B949E),
    outlineVariant = Color(0xFFD0D4DA),

    inverseSurface = OnSurfaceLight,
    inverseOnSurface = SurfaceLight,
    inversePrimary = DeepBlueLight,

    scrim = Color(0xFF000000)
)
