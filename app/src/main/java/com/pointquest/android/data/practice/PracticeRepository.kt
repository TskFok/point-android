package com.pointquest.android.data.practice

import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppResult

interface PracticeRepository {
    suspend fun summary(): AppResult<PracticeSummary> =
        summary(LearnerLanguage.ALL)
    suspend fun summary(language: LearnerLanguage = LearnerLanguage.ALL): AppResult<PracticeSummary> =
        error("PracticeRepository.summary(language) is not implemented.")

    suspend fun nextQuestion(excludeIds: List<String>): AppResult<Question> =
        nextQuestion(excludeIds, LearnerLanguage.ALL)
    suspend fun nextQuestion(
        excludeIds: List<String>,
        language: LearnerLanguage = LearnerLanguage.ALL,
    ): AppResult<Question> = error("PracticeRepository.nextQuestion(language) is not implemented.")

    suspend fun previewQuestions(
        count: Int,
        language: LearnerLanguage = LearnerLanguage.ALL,
    ): AppResult<List<Question>> = error("PracticeRepository.previewQuestions is not implemented.")

    suspend fun answerFirst(questionId: String, selectedOptionId: String): AppResult<AnswerResult> =
        answerFirst(questionId, selectedOptionId, null)
    suspend fun answerFirst(
        questionId: String,
        selectedOptionId: String,
        idempotencyKey: String? = null,
    ): AppResult<AnswerResult> = error("PracticeRepository.answerFirst(idempotencyKey) is not implemented.")

    suspend fun wrongQuestions(page: Int): AppResult<Page<WrongQuestion>> =
        wrongQuestions(page, LearnerLanguage.ALL)
    suspend fun wrongQuestions(page: Int, language: LearnerLanguage = LearnerLanguage.ALL): AppResult<Page<WrongQuestion>> =
        error("PracticeRepository.wrongQuestions(language) is not implemented.")

    suspend fun answerWrong(
        questionId: String,
        selectedOptionId: String,
        idempotencyKey: String? = null,
    ): AppResult<AnswerResult>
}
