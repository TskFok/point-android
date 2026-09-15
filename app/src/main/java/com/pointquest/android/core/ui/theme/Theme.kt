package com.pointquest.android.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val PointQuestColorScheme = lightColorScheme(
    primary = PaperTerracotta,
    onPrimary = PaperSurface,
    primaryContainer = PaperAccentContainer,
    onPrimaryContainer = Color(0xFF57210F),
    inversePrimary = PaperAccentContainer,
    secondary = Color(0xFF715341),
    onSecondary = PaperSurface,
    secondaryContainer = PaperWarmSurface,
    onSecondaryContainer = PaperInk,
    tertiary = SuccessText,
    onTertiary = PaperSurface,
    tertiaryContainer = Color(0xFFE7EEE2),
    onTertiaryContainer = Color(0xFF20482C),
    background = PaperBackground,
    onBackground = PaperInk,
    surface = PaperSurface,
    onSurface = PaperInk,
    surfaceVariant = Color(0xFFEEE8DE),
    onSurfaceVariant = Color(0xFF686155),
    surfaceTint = Color.Transparent,
    inverseSurface = PaperInk,
    inverseOnSurface = PaperBackground,
    error = ErrorText,
    onError = PaperSurface,
    errorContainer = Color(0xFFF8E4DA),
    onErrorContainer = Color(0xFF7B211B),
    outline = PaperOutline,
    outlineVariant = PaperLine,
    scrim = Color(0xFF201A15),
    surfaceBright = PaperSurface,
    surfaceDim = Color(0xFFE4DDD1),
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperBackground,
    surfaceContainer = Color(0xFFF0EAE0),
    surfaceContainerHigh = Color(0xFFECE4D8),
    surfaceContainerHighest = Color(0xFFE5DCCF),
)

@Composable
fun PointQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PointQuestColorScheme,
        typography = PointQuestTypography,
        shapes = PointQuestShapes,
        content = content,
    )
}
