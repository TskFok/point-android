package com.pointquest.android.feature.practice

import com.pointquest.android.app.PracticeMode
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.ui.UiText

data class QuestionUiState(
    val mode: PracticeMode,
    val loading: Boolean = true,
    val question: Question? = null,
    val selectedOptionId: String? = null,
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val result: AnswerResult? = null,
    val completed: Boolean = false,
    val error: UiText? = null,
) {
    val selectionEnabled: Boolean
        get() = question != null && !loading && !submitting && !submitted

    val submitEnabled: Boolean
        get() = selectionEnabled && selectedOptionId != null
}

sealed interface QuestionEvent {
    data object DraftMissing : QuestionEvent

    data class WrongMastered(
        val questionId: String,
        val returnToList: Boolean,
    ) : QuestionEvent
}
