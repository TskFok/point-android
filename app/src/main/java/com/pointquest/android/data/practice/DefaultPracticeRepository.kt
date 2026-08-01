package com.pointquest.android.data.practice

import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.AuthorizedCallExecutor
import com.pointquest.android.core.network.RetryExecutor
import com.pointquest.android.data.gateway.StudentGateway

class DefaultPracticeRepository(
    private val gateway: StudentGateway,
    private val authorized: AuthorizedCallExecutor,
    private val retry: RetryExecutor,
) : PracticeRepository {
    override suspend fun summary(): AppResult<PracticeSummary> = read { gateway.practiceSummary() }

    override suspend fun nextQuestion(excludeIds: List<String>): AppResult<Question> {
        val mostRecentDistinct = excludeIds.asReversed().distinct().asReversed().takeLast(MAX_EXCLUDE_IDS)
        return read { gateway.randomQuestion(mostRecentDistinct) }
    }

    override suspend fun answerFirst(
        questionId: String,
        selectedOptionId: String,
    ): AppResult<AnswerResult> = write(AnswerPayload(questionId, selectedOptionId)) { payload, key ->
        gateway.answerFirst(payload.questionId, payload.selectedOptionId, key)
    }

    override suspend fun wrongQuestions(page: Int): AppResult<Page<WrongQuestion>> =
        read { gateway.wrongQuestions(page, PAGE_SIZE) }

    override suspend fun answerWrong(
        questionId: String,
        selectedOptionId: String,
    ): AppResult<AnswerResult> = write(AnswerPayload(questionId, selectedOptionId)) { payload, key ->
        gateway.answerWrong(payload.questionId, payload.selectedOptionId, key)
    }

    private suspend fun <T> read(operation: suspend () -> AppResult<T>): AppResult<T> =
        retry.executeRead { authorized.execute(operation) }

    private suspend fun <T> write(
        payload: AnswerPayload,
        operation: suspend (AnswerPayload, String) -> AppResult<T>,
    ): AppResult<T> = retry.executeIdempotent(payload) { frozen ->
        authorized.execute { operation(frozen.payload, frozen.key) }
    }

    private data class AnswerPayload(val questionId: String, val selectedOptionId: String)

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_EXCLUDE_IDS = 50
    }
}
