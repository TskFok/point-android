package com.pointquest.android.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val MaterialTypography = Typography()

val PointQuestTypography = Typography(
    headlineSmall = MaterialTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = MaterialTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = MaterialTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = MaterialTypography.bodyLarge,
    bodyMedium = MaterialTypography.bodyMedium,
    labelLarge = MaterialTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)
