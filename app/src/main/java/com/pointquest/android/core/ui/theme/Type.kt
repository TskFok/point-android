package com.pointquest.android.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pointquest.android.R

private val PaperHeadingFamily = FontFamily(
    Font(R.font.paper_serif_semibold, FontWeight.SemiBold),
)

private fun heading(size: Int, height: Int) = TextStyle(
    fontFamily = PaperHeadingFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = 0.sp,
)

private fun body(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = 0.sp,
)

val PointQuestTypography = Typography(
    displayLarge = heading(56, 64),
    displayMedium = heading(44, 52),
    displaySmall = heading(36, 44),
    headlineLarge = heading(32, 44),
    headlineMedium = heading(28, 38),
    headlineSmall = heading(24, 34),
    titleLarge = heading(22, 30),
    titleMedium = body(16, 24, FontWeight.SemiBold),
    titleSmall = body(14, 22, FontWeight.SemiBold),
    bodyLarge = body(16, 26),
    bodyMedium = body(14, 22),
    bodySmall = body(12, 18),
    labelLarge = body(14, 20, FontWeight.Medium),
    labelMedium = body(12, 18, FontWeight.Medium),
    labelSmall = body(11, 16, FontWeight.Medium),
)
