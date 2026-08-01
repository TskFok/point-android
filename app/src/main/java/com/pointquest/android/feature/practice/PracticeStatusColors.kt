package com.pointquest.android.feature.practice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.pointquest.android.core.ui.theme.ErrorRed
import com.pointquest.android.core.ui.theme.ErrorText
import com.pointquest.android.core.ui.theme.SuccessGreen
import com.pointquest.android.core.ui.theme.SuccessText

enum class PracticeAnswerStatus { Correct, Incorrect }

data class PracticeStatusVisualColors(
    val text: Color,
    val icon: Color,
    val accent: Color,
    val container: Color,
)

object PracticeStatusColors {
    fun result(status: PracticeAnswerStatus, container: Color): PracticeStatusVisualColors =
        palette(status, container)

    fun option(status: PracticeAnswerStatus, baseContainer: Color): PracticeStatusVisualColors {
        val colors = palette(status, baseContainer)
        val tintAlpha = when (status) {
            PracticeAnswerStatus.Correct -> CORRECT_TINT_ALPHA
            PracticeAnswerStatus.Incorrect -> INCORRECT_TINT_ALPHA
        }
        return colors.copy(container = colors.accent.copy(alpha = tintAlpha).compositeOver(baseContainer))
    }

    private fun palette(status: PracticeAnswerStatus, container: Color): PracticeStatusVisualColors {
        val (foreground, accent) = when (status) {
            PracticeAnswerStatus.Correct -> SuccessText to SuccessGreen
            PracticeAnswerStatus.Incorrect -> ErrorText to ErrorRed
        }
        return PracticeStatusVisualColors(
            text = foreground,
            icon = foreground,
            accent = accent,
            container = container,
        )
    }

    private const val CORRECT_TINT_ALPHA = .14f
    private const val INCORRECT_TINT_ALPHA = .12f
}
