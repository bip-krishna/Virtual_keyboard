package com.visionboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalIsDeveloperMode = staticCompositionLocalOf { false }

@Composable
fun VisionBoardTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isDeveloperMode: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val typography = if (isDeveloperMode) DeveloperModeTypography else VisionBoardTypography

    CompositionLocalProvider(LocalIsDeveloperMode provides isDeveloperMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = VisionBoardShapes,
            content = content
        )
    }
}
