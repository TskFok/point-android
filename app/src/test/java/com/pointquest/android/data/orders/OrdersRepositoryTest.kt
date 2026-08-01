package com.pointquest.android.data.orders

import com.pointquest.android.core.auth.RefreshCoordinator
import com.pointquest.android.core.auth.SessionManager
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStore
import com.pointquest.android.core.auth.StoredRefreshSession
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.OrderStatus
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.AuthorizedCallExecutor
import com.pointquest.android.core.network.DelayProvider
import com.pointquest.android.core.network.IdempotencyKeyFactory
import com.pointquest.android.core.network.JitterSource
import com.pointquest.android.core.network.RetryExecutor
import com.pointquest.android.data.gateway.PublicAuthGateway
import com.pointquest.android.data.gateway.StudentGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdersRepositoryTest {
    @Test
    fun checkoutRetriesConcurrentModificationWithSameKey() = runBlocking {
        val gateway = FakeStudentGateway().apply {
            createOrderResults += failure(409, "CONCURRENT_MODIFICATION")
            createOrderResults += AppResult.Success(pendingOrder)
        }

        val result = repository(gateway).redeem("p1")

        assertTrue(result is AppResult.Success)
        assertEquals(2, gateway.createOrderCalls.size)
        assertEquals(1, gateway.createOrderCalls.map { it.key }.distinct().size)
        UUID.fromString(gateway.createOrderCalls.singleKey())
        assertTrue(gateway.createOrderCalls.all { it.productId == "p1" })
    }

    @Test
    fun redeemDoesNotRetryStableBusinessErrorsOrIdempotencyConflicts() = runBlocking {
        val errors = listOf(
            failure(409, "INSUFFICIENT_POINTS"),
            failure(409, "OUT_OF_STOCK"),
            failure(409, "PRODUCT_INACTIVE"),
            failure(409, "IDEMPOTENCY_CONFLICT"),
        )

        errors.forEach { expected ->
            val gateway = FakeStudentGateway().apply { createOrderResults += expected }

            val result = repository(gateway).redeem("p1")

            assertSame(expected.error, (result as AppResult.Failure).error)
            assertEquals("Unexpected retry for ${expected.error.code}", 1, gateway.createOrderCalls.size)
        }
    }

    @Test
    fun pageUsesFixedPageSizeAndPreservesPendingPickupStatus() = runBlocking {
        val gateway = FakeStudentGateway().apply {
            orderPageResult = AppResult.Success(Page(listOf(pendingOrder), PageMeta(2, 20, 1, 1)))
        }

        val result = repository(gateway).page(2)

        assertEquals(listOf(2 to 20), gateway.orderPageCalls)
        assertEquals(OrderStatus.PENDING_PICKUP, (result as AppResult.Success).value.items.single().status)
    }

    @Test
    fun pageRetriesRetryableReadFailureWithTheSamePageRequest() = runBlocking {
        val expected = Page(listOf(pendingOrder), PageMeta(2, 20, 1, 1))
        val gateway = FakeStudentGateway().apply {
            orderPageResults += failure(503, "SERVICE_UNAVAILABLE")
            orderPageResults += AppResult.Success(expected)
        }

        val result = repository(gateway).page(2)

        assertEquals(expected, (result as AppResult.Success).value)
        assertEquals(listOf(2 to 20, 2 to 20), gateway.orderPageCalls)
    }

    @Test
    fun detailPreservesCompletedAndCancelledStatuses() = runBlocking {
        val gateway = FakeStudentGateway().apply {
            orderResults += AppResult.Success(completedOrder)
            orderResults += AppResult.Success(cancelledOrder)
        }
        val repository = repository(gateway)

        val completed = repository.detail("completed")
        val cancelled = repository.detail("cancelled")

        assertEquals(OrderStatus.COMPLETED, (completed as AppResult.Success).value.status)
        assertEquals(OrderStatus.CANCELLED, (cancelled as AppResult.Success).value.status)
        assertEquals(listOf("completed", "cancelled"), gateway.orderIds)
    }

    private suspend fun repository(gateway: StudentGateway): DefaultOrdersRepository =
        DefaultOrdersRepository(gateway, authorizedExecutor(), retryExecutor())

    private suspend fun authorizedExecutor(): AuthorizedCallExecutor {
        val state = SessionState()
        val manager = SessionManager(MemoryStore(), state)
        manager.install(tokenBundle())
        return AuthorizedCallExecutor(state, RefreshCoordinator(FakeAuthGateway(), manager, state, clock), clock)
    }

    private fun retryExecutor() = RetryExecutor(
        delayProvider = DelayProvider { },
        jitterSource = JitterSource { 0 },
        idempotencyKeyFactory = IdempotencyKeyFactory { UUID.randomUUID().toString() },
    )

    private class FakeStudentGateway : StudentGateway {
        data class CreateOrderCall(val productId: String, val key: String)

        val createOrderCalls = mutableListOf<CreateOrderCall>()
        val createOrderResults = ArrayDeque<AppResult<Order>>()
        val orderIds = mutableListOf<String>()
        val orderResults = ArrayDeque<AppResult<Order>>()
        val orderPageCalls = mutableListOf<Pair<Int, Int>>()
        val orderPageResults = ArrayDeque<AppResult<Page<Order>>>()
        var orderPageResult: AppResult<Page<Order>> = AppResult.Success(Page(emptyList(), PageMeta(1, 20, 0, 0)))

        override suspend fun createOrder(productId: String, key: String): AppResult<Order> {
            createOrderCalls += CreateOrderCall(productId, key)
            return createOrderResults.removeFirstOrNull() ?: AppResult.Success(pendingOrder)
        }

        override suspend fun orders(page: Int, pageSize: Int): AppResult<Page<Order>> {
            orderPageCalls += page to pageSize
            return orderPageResults.removeFirstOrNull() ?: orderPageResult
        }

        override suspend fun order(id: String): AppResult<Order> {
            orderIds += id
            return orderResults.removeFirstOrNull() ?: AppResult.Success(pendingOrder)
        }

        override suspend fun practiceSummary(): AppResult<PracticeSummary> = error("unused")
        override suspend fun randomQuestion(excludeIds: List<String>): AppResult<Question> = error("unused")
        override suspend fun answerFirst(questionId: String, optionId: String, key: String): AppResult<AnswerResult> = error("unused")
        override suspend fun wrongQuestions(page: Int, pageSize: Int): AppResult<Page<WrongQuestion>> = error("unused")
        override suspend fun answerWrong(questionId: String, optionId: String, key: String): AppResult<AnswerResult> = error("unused")
        override suspend fun pointBalance(): AppResult<Int> = error("unused")
        override suspend fun pointLedger(page: Int, pageSize: Int): AppResult<Page<PointLedgerEntry>> = error("unused")
        override suspend fun products(search: String?, page: Int, pageSize: Int): AppResult<Page<Product>> = error("unused")
        override suspend fun product(id: String): AppResult<Product> = error("unused")
    }

    private class MemoryStore : SessionStore {
        private var value: StoredRefreshSession? = null
        override suspend fun read() = value
        override suspend fun write(value: StoredRefreshSession) { this.value = value }
        override suspend fun clear() { value = null }
    }

    private class FakeAuthGateway : PublicAuthGateway {
        override suspend fun register(username: String, password: String) = error("unused")
        override suspend fun login(username: String, password: String) = error("unused")
        override suspend fun refresh(refreshToken: String) = AppResult.Success(tokenBundle())
        override suspend fun logout(refreshToken: String) = AppResult.Success(Unit)
    }

    private companion object {
        val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
        val pendingOrder = order("pending", OrderStatus.PENDING_PICKUP)
        val completedOrder = order("completed", OrderStatus.COMPLETED)
        val cancelledOrder = order("cancelled", OrderStatus.CANCELLED)

        fun order(id: String, status: OrderStatus) = Order(
            id, "NO-$id", "u1", "p1", "Pencil", "products/550e8400-e29b-41d4-a716-446655440000.png",
            10, status, 32, now, null, null, null,
        )

        fun tokenBundle() = TokenBundle(
            "access", now.plusSeconds(300), "refresh", now.plusSeconds(3600),
            User("u1", "student", UserRole.STUDENT, 42),
        )

        fun failure(status: Int, code: String) = AppResult.Failure(AppError(status, code, code, null))

        fun List<FakeStudentGateway.CreateOrderCall>.singleKey() = first().key
    }
}
