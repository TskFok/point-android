package com.pointquest.android.feature.practice

import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.ui.UiText

enum class PreviewPhase {
    SETUP,
    QUIZ,
    SUMMARY,
}

data class PreviewItem(
    val question: Question,
    val selectedOptionId: String? = null,
    val submissionOptionId: String? = null,
    val submissionKey: String,
    val result: AnswerResult? = null,
    val submitError: UiText? = null,
    val alreadyAnswered: Boolean = false,
) {
    val answered: Boolean
        get() = result != null || alreadyAnswered
}

data class PreviewUiState(
    val count: Int? = DEFAULT_PREVIEW_COUNT,
    val language: LearnerLanguage = LearnerLanguage.ALL,
    val phase: PreviewPhase = PreviewPhase.SETUP,
    val items: List<PreviewItem> = emptyList(),
    val currentIndex: Int = 0,
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val loadError: UiText? = null,
    val emptyPool: Boolean = false,
) {
    val completed: Boolean
        get() = phase == PreviewPhase.SUMMARY || (items.isNotEmpty() && items.all { it.answered })

    val correctCount: Int
        get() = items.count { it.result?.correct == true }

    val skippedCount: Int
        get() = items.count { it.alreadyAnswered }

    val pointsEarned: Int
        get() = items.sumOf { it.result?.pointsAwarded ?: 0 }

    val currentItem: PreviewItem?
        get() = items.getOrNull(currentIndex)

    val countValid: Boolean
        get() = count in MIN_PREVIEW_COUNT..MAX_PREVIEW_COUNT

    companion object {
        const val MIN_PREVIEW_COUNT = 1
        const val MAX_PREVIEW_COUNT = 50
        const val DEFAULT_PREVIEW_COUNT = 5
        val PRESET_COUNTS = listOf(5, 10, 20, 50)
    }
}
