package com.pointquest.android.feature.practice

import com.pointquest.android.app.PracticeMode
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.ui.UiText

data class QuestionQueueItem(
    val question: Question,
    val selectedOptionId: String? = null,
    val submissionKey: String,
    val submissionOptionId: String? = null,
    val result: AnswerResult? = null,
    val submitError: UiText? = null,
)

data class QuestionUiState(
    val mode: PracticeMode,
    val loading: Boolean = true,
    val queue: List<QuestionQueueItem> = emptyList(),
    val currentIndex: Int = 0,
    val loadingNext: Boolean = false,
    val submitting: Boolean = false,
    val completed: Boolean = false,
    val error: UiText? = null,
    val tailError: UiText? = null,
) {
    val currentItem: QuestionQueueItem?
        get() = queue.getOrNull(currentIndex)

    val question: Question?
        get() = currentItem?.question

    val selectedOptionId: String?
        get() = currentItem?.selectedOptionId

    val result: AnswerResult?
        get() = currentItem?.result

    val submitted: Boolean
        get() = result != null

    val hasPrevious: Boolean
        get() = currentIndex > 0

    val hasNextInQueue: Boolean
        get() = currentIndex < queue.lastIndex

    val selectionEnabled: Boolean
        get() = question != null &&
            !loading &&
            !loadingNext &&
            !submitting &&
            !submitted &&
            currentItem?.submissionOptionId == null

    val submitEnabled: Boolean
        get() = question != null &&
            !loading &&
            !loadingNext &&
            !submitting &&
            !submitted &&
            (selectedOptionId != null || currentItem?.submissionOptionId != null)
}

sealed interface QuestionEvent {
    data object DraftMissing : QuestionEvent

    data class WrongMastered(
        val questionId: String,
        val returnToList: Boolean,
    ) : QuestionEvent
}
