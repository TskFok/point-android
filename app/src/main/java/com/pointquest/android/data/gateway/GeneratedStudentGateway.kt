package com.pointquest.android.data.gateway

import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.toAppResult
import com.pointquest.android.core.network.toDomain
import com.pointquest.android.core.network.toNetworkError
import com.pointquest.android.generated.api.DefaultApi
import com.pointquest.android.generated.model.AnswerQuestionRequestDto
import com.pointquest.android.generated.model.CreateOrderRequestDto
import java.io.IOException
import kotlinx.coroutines.CancellationException

class GeneratedStudentGateway(
    private val api: DefaultApi,
) : StudentGateway {
    override suspend fun practiceSummary(): AppResult<PracticeSummary> = networkCall {
        api.practiceGetSummary().toAppResult { it.toDomain() }
    }

    override suspend fun randomQuestion(excludeIds: List<String>): AppResult<Question> = networkCall {
        api.practiceGetRandomQuestion(excludeIds.joinToString(",").ifEmpty { null })
            .toAppResult { it.toDomain() }
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

    override suspend fun wrongQuestions(page: Int, pageSize: Int): AppResult<Page<WrongQuestion>> =
        networkCall {
            api.practiceListWrongQuestions(page, pageSize).toAppResult { response ->
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
}
