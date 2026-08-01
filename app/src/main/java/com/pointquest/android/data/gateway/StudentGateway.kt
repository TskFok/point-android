package com.pointquest.android.data.gateway

import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppResult

interface StudentGateway {
    suspend fun practiceSummary(): AppResult<PracticeSummary>
    suspend fun randomQuestion(excludeIds: List<String>): AppResult<Question>
    suspend fun answerFirst(questionId: String, optionId: String, key: String): AppResult<AnswerResult>
    suspend fun wrongQuestions(page: Int, pageSize: Int): AppResult<Page<WrongQuestion>>
    suspend fun answerWrong(questionId: String, optionId: String, key: String): AppResult<AnswerResult>
    suspend fun pointBalance(): AppResult<Int>
    suspend fun pointLedger(page: Int, pageSize: Int): AppResult<Page<PointLedgerEntry>>
    suspend fun products(search: String?, page: Int, pageSize: Int): AppResult<Page<Product>>
    suspend fun product(id: String): AppResult<Product>
    suspend fun createOrder(productId: String, key: String): AppResult<Order>
    suspend fun orders(page: Int, pageSize: Int): AppResult<Page<Order>>
    suspend fun order(id: String): AppResult<Order>
}
