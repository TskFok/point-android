package com.pointquest.android.feature.home

import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.ui.UiText

data class HomeUiState(
    val language: LearnerLanguage = LearnerLanguage.ALL,
    val username: String = "",
    val summary: PracticeSummary? = null,
    val balance: Int? = null,
    val loading: Boolean = true,
    val error: UiText? = null,
    val canRetry: Boolean = false,
)
