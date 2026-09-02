package com.pointquest.android.data.gateway

import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.toAppResult
import com.pointquest.android.core.network.toDomain
import com.pointquest.android.core.network.toNetworkError
import com.pointquest.android.generated.api.DefaultApi
import com.pointquest.android.generated.model.AnswerQuestionRequestDto
import com.pointquest.android.generated.model.CreateOrderRequestDto
import com.pointquest.android.generated.model.LearnerQuestionDto
import com.pointquest.android.generated.model.PreviewQuestionDto
import java.io.IOException
import kotlinx.coroutines.CancellationException

class GeneratedStudentGateway(
    private val api: DefaultApi,
) : StudentGateway {
    override suspend fun practiceSummary(language: LearnerLanguage): AppResult<PracticeSummary> = networkCall {
        api.practiceGetSummary(language.toSummaryLangCode()).toAppResult { it.toDomain() }
    }

    override suspend fun randomQuestion(
        excludeIds: List<String>,
        language: LearnerLanguage,
    ): AppResult<Question> = networkCall {
        api.practiceGetRandomQuestion(
            excludeIds.joinToString(",").ifEmpty { null },
            language.toRandomQuestionLangCode(),
        )
            .toAppResult { it.toDomain() }
    }

    override suspend fun previewQuestions(
        count: Int,
        language: LearnerLanguage,
    ): AppResult<List<Question>> = networkCall {
        api.practiceGetPreviewQuestions(count, language.toPreviewQuestionsLangCode())
            .toAppResult { response -> response.data.map { it.toDomainQuestion() } }
    }

    override suspend fun answerFirst(
        questionId: String,
        optionId: String,
        key: String,
    ): AppResult<AnswerResult> = networkCall {
        api.practiceAnswerQuestion(
            questionId,
            key,
            AnswerQuestionRequestDto(selectedOptionId = optionId),
            xCSRFToken = null,
        ).toAppResult { it.toDomain() }
    }

    override suspend fun wrongQuestions(
        page: Int,
        pageSize: Int,
        language: LearnerLanguage,
    ): AppResult<Page<WrongQuestion>> =
        networkCall {
            api.practiceListWrongQuestions(page, pageSize, language.toWrongQuestionsLangCode()).toAppResult { response ->
                Page(response.data.map { it.toDomain() }, response.meta.toDomain())
            }
        }

    override suspend fun answerWrong(
        questionId: String,
        optionId: String,
        key: String,
    ): AppResult<AnswerResult> = networkCall {
        api.practiceRetryWrongQuestion(
            questionId,
            key,
            AnswerQuestionRequestDto(selectedOptionId = optionId),
            xCSRFToken = null,
        ).toAppResult { it.toDomain() }
    }

    override suspend fun pointBalance(): AppResult<Int> = networkCall {
        api.pointsGetBalance().toAppResult { it.balance }
    }

    override suspend fun pointLedger(page: Int, pageSize: Int): AppResult<Page<PointLedgerEntry>> =
        networkCall {
            api.pointsListLedger(page, pageSize).toAppResult { response ->
                Page(response.data.map { it.toDomain() }, response.meta.toDomain())
            }
        }

    override suspend fun products(search: String?, page: Int, pageSize: Int): AppResult<Page<Product>> =
        networkCall {
            api.productsList(
                search = search,
                isActive = true,
                page = page,
                pageSize = pageSize,
            ).toAppResult { response ->
                Page(response.data.map { it.toDomain() }, response.meta.toDomain())
            }
        }

    override suspend fun product(id: String): AppResult<Product> = networkCall {
        api.productsGet(id).toAppResult { it.toDomain() }
    }

    override suspend fun createOrder(productId: String, key: String): AppResult<Order> = networkCall {
        api.ordersCreate(key, CreateOrderRequestDto(productId), xCSRFToken = null)
            .toAppResult { it.toDomain() }
    }

    override suspend fun orders(page: Int, pageSize: Int): AppResult<Page<Order>> = networkCall {
        api.ordersList(page, pageSize).toAppResult { response ->
            Page(response.data.map { it.toDomain() }, response.meta.toDomain())
        }
    }

    override suspend fun order(id: String): AppResult<Order> = networkCall {
        api.ordersGet(id).toAppResult { it.toDomain() }
    }

    override suspend fun currentUser(): AppResult<User> = networkCall {
        api.authGetCurrentUser().toAppResult { it.user.toDomain() }
    }

    private suspend fun <T> networkCall(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: IOException) {
        AppResult.Failure(failure.toNetworkError())
    } catch (failure: RuntimeException) {
        AppResult.Failure(
            AppError(
                httpStatus = null,
                code = "INVALID_RESPONSE",
                message = "Server response is invalid",
                requestId = null,
                cause = failure,
            ),
        )
    }

    private fun PreviewQuestionDto.toDomainQuestion(): Question = LearnerQuestionDto(
        basePoints = basePoints,
        id = id,
        options = options,
        stem = stem,
    ).toDomain()

    private fun LearnerLanguage.toSummaryLangCode(): DefaultApi.LangCodePracticeGetSummary? =
        when (this) {
            LearnerLanguage.ALL -> null
            LearnerLanguage.EN -> DefaultApi.LangCodePracticeGetSummary.EN
            LearnerLanguage.JA -> DefaultApi.LangCodePracticeGetSummary.JA
            LearnerLanguage.IT -> DefaultApi.LangCodePracticeGetSummary.IT
            LearnerLanguage.FR -> DefaultApi.LangCodePracticeGetSummary.FR
            LearnerLanguage.DE -> DefaultApi.LangCodePracticeGetSummary.DE
        }

    private fun LearnerLanguage.toRandomQuestionLangCode(): DefaultApi.LangCodePracticeGetRandomQuestion? =
        when (this) {
            LearnerLanguage.ALL -> null
            LearnerLanguage.EN -> DefaultApi.LangCodePracticeGetRandomQuestion.EN
            LearnerLanguage.JA -> DefaultApi.LangCodePracticeGetRandomQuestion.JA
            LearnerLanguage.IT -> DefaultApi.LangCodePracticeGetRandomQuestion.IT
            LearnerLanguage.FR -> DefaultApi.LangCodePracticeGetRandomQuestion.FR
            LearnerLanguage.DE -> DefaultApi.LangCodePracticeGetRandomQuestion.DE
        }

    private fun LearnerLanguage.toPreviewQuestionsLangCode(): DefaultApi.LangCodePracticeGetPreviewQuestions? =
        when (this) {
            LearnerLanguage.ALL -> null
            LearnerLanguage.EN -> DefaultApi.LangCodePracticeGetPreviewQuestions.EN
            LearnerLanguage.JA -> DefaultApi.LangCodePracticeGetPreviewQuestions.JA
            LearnerLanguage.IT -> DefaultApi.LangCodePracticeGetPreviewQuestions.IT
            LearnerLanguage.FR -> DefaultApi.LangCodePracticeGetPreviewQuestions.FR
            LearnerLanguage.DE -> DefaultApi.LangCodePracticeGetPreviewQuestions.DE
        }

    private fun LearnerLanguage.toWrongQuestionsLangCode(): DefaultApi.LangCodePracticeListWrongQuestions? =
        when (this) {
            LearnerLanguage.ALL -> null
            LearnerLanguage.EN -> DefaultApi.LangCodePracticeListWrongQuestions.EN
            LearnerLanguage.JA -> DefaultApi.LangCodePracticeListWrongQuestions.JA
            LearnerLanguage.IT -> DefaultApi.LangCodePracticeListWrongQuestions.IT
            LearnerLanguage.FR -> DefaultApi.LangCodePracticeListWrongQuestions.FR
            LearnerLanguage.DE -> DefaultApi.LangCodePracticeListWrongQuestions.DE
        }
}
