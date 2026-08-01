package com.pointquest.android.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PointQuestColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

@Composable
fun PointQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PointQuestColorScheme,
        typography = Typography(),
        content = content,
    )
}
