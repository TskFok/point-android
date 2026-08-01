package com.pointquest.android.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun specifiedTertiaryAndErrorBaseColorsRemainUnchanged() {
        assertEquals(Color(0xFF238B57), PointQuestColorScheme.tertiary)
        assertEquals(Color(0xFFD64545), PointQuestColorScheme.error)
    }

    @Test
    fun allConfiguredTextColorRolesMeetWcagAaForNormalText() {
        val pairs = listOf(
            "primary/onPrimary" to (PointQuestColorScheme.primary to PointQuestColorScheme.onPrimary),
            "secondary/onSecondary" to (PointQuestColorScheme.secondary to PointQuestColorScheme.onSecondary),
            "background/onBackground" to (PointQuestColorScheme.background to PointQuestColorScheme.onBackground),
            "surface/onSurface" to (PointQuestColorScheme.surface to PointQuestColorScheme.onSurface),
            "tertiary/onTertiary" to (PointQuestColorScheme.tertiary to PointQuestColorScheme.onTertiary),
            "error/onError" to (PointQuestColorScheme.error to PointQuestColorScheme.onError),
        )
        val failures = pairs.mapNotNull { (name, colors) ->
            val contrast = contrastRatio(colors.first, colors.second)
            if (contrast < MIN_NORMAL_TEXT_CONTRAST) "$name=${"%.3f".format(contrast)}:1" else null
        }

        assertTrue(
            "Theme text pairs below 4.5:1: ${failures.joinToString()}",
            failures.isEmpty(),
        )
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

    private companion object {
        const val MIN_NORMAL_TEXT_CONTRAST = 4.5
    }
}
