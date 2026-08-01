package com.pointquest.android.feature.practice

import androidx.compose.ui.graphics.Color
import com.pointquest.android.core.ui.theme.ClassroomBackground
import com.pointquest.android.core.ui.theme.ClassroomSurface
import com.pointquest.android.core.ui.theme.ErrorRed
import com.pointquest.android.core.ui.theme.ErrorText
import com.pointquest.android.core.ui.theme.SuccessGreen
import com.pointquest.android.core.ui.theme.SuccessText
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeStatusColorsTest {
    @Test
    fun resultCardTextAndIconForegroundsMeetWcagAaOnProductionWhiteSurface() {
        val palettes = listOf(
            PracticeAnswerStatus.Correct to PracticeStatusColors.result(
                PracticeAnswerStatus.Correct,
                ClassroomSurface,
            ),
            PracticeAnswerStatus.Incorrect to PracticeStatusColors.result(
                PracticeAnswerStatus.Incorrect,
                ClassroomSurface,
            ),
        )

        palettes.forEach { (status, colors) ->
            assertStatusForeground(status, colors)
            assertContrast("$status result text", colors.text, colors.container)
            assertContrast("$status result icon", colors.icon, colors.container)
        }
    }

    @Test
    fun optionStatusTextAndIconsMeetWcagAaOnActualTintedContainers() {
        val palettes = listOf(
            PracticeAnswerStatus.Correct to PracticeStatusColors.option(
                PracticeAnswerStatus.Correct,
                ClassroomBackground,
            ),
            PracticeAnswerStatus.Incorrect to PracticeStatusColors.option(
                PracticeAnswerStatus.Incorrect,
                ClassroomBackground,
            ),
        )

        palettes.forEach { (status, colors) ->
            assertStatusForeground(status, colors)
            assertContrast("$status option text", colors.text, colors.container)
            assertContrast("$status option icon", colors.icon, colors.container)
        }
    }

    private fun assertStatusForeground(status: PracticeAnswerStatus, colors: PracticeStatusVisualColors) {
        val expectedText = when (status) {
            PracticeAnswerStatus.Correct -> SuccessText
            PracticeAnswerStatus.Incorrect -> ErrorText
        }
        val expectedAccent = when (status) {
            PracticeAnswerStatus.Correct -> SuccessGreen
            PracticeAnswerStatus.Incorrect -> ErrorRed
        }
        assertEquals(expectedText, colors.text)
        assertEquals(expectedText, colors.icon)
        assertEquals(expectedAccent, colors.accent)
    }

    private fun assertContrast(name: String, foreground: Color, background: Color) {
        val contrast = contrastRatio(foreground, background)
        assertTrue("$name contrast=${"%.3f".format(contrast)}:1", contrast >= 4.5)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red.toDouble()) +
            0.7152 * linearize(color.green.toDouble()) +
            0.0722 * linearize(color.blue.toDouble())

    private fun linearize(channel: Double): Double = if (channel <= 0.04045) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }
}
