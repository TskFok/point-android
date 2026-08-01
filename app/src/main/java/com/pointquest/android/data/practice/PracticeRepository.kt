package com.pointquest.android.data.practice

import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppResult

interface PracticeRepository {
    suspend fun summary(): AppResult<PracticeSummary>
    suspend fun nextQuestion(excludeIds: List<String>): AppResult<Question>
    suspend fun answerFirst(questionId: String, selectedOptionId: String): AppResult<AnswerResult>
    suspend fun wrongQuestions(page: Int): AppResult<Page<WrongQuestion>>
    suspend fun answerWrong(questionId: String, selectedOptionId: String): AppResult<AnswerResult>
}
