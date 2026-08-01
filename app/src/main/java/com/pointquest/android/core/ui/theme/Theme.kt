package com.pointquest.android.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

internal val PointQuestColorScheme = lightColorScheme(
    primary = ClassroomBlue,
    onPrimary = ClassroomOnBlue,
    secondary = RewardYellow,
    onSecondary = ClassroomInk,
    tertiary = SuccessGreen,
    onTertiary = ClassroomOnStrongColor,
    background = ClassroomBackground,
    onBackground = ClassroomInk,
    surface = ClassroomSurface,
    onSurface = ClassroomInk,
    error = ErrorRed,
    onError = ClassroomOnStrongColor,
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
