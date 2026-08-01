package com.pointquest.android.feature.orders

import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.OrderStatus
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.orders.OrdersRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderDetailViewModelTest {
    @Test
    fun invalidStatusReloadsDetailWithGetExactlyOnceAndInitializationIsIdempotent() = runTest {
        val repository = FakeOrdersRepository(
            ArrayDeque(listOf(failure("ORDER_INVALID_STATUS"), AppResult.Success(order("o1")))),
        )
        val viewModel = OrderDetailViewModel("o1", repository, backgroundScope)

        viewModel.initialize()?.join()

        assertEquals(2, repository.detailCalls)
        assertEquals(0, repository.redeemCalls)
        assertEquals(order("o1"), viewModel.uiState.value.order)
        assertFalse(viewModel.uiState.value.loading)
        assertNull(viewModel.initialize())
    }

    @Test
    fun repeatedInvalidStatusStopsAfterOneRefreshAndShowsError() = runTest {
        val repository = FakeOrdersRepository(
            ArrayDeque(listOf(failure("ORDER_INVALID_STATUS"), failure("ORDER_INVALID_STATUS"))),
        )
        val viewModel = OrderDetailViewModel("o1", repository, backgroundScope)

        viewModel.initialize()?.join()

        assertEquals(2, repository.detailCalls)
        assertTrue(viewModel.uiState.value.error != null)
        assertFalse(viewModel.uiState.value.loading)
    }

    private class FakeOrdersRepository(
        private val details: ArrayDeque<AppResult<Order>>,
    ) : OrdersRepository {
        var detailCalls = 0
        var redeemCalls = 0
        override suspend fun detail(id: String): AppResult<Order> {
            detailCalls++
            return details.removeFirst()
        }
        override suspend fun redeem(productId: String): AppResult<Order> {
            redeemCalls++
            return error("must not write")
        }
        override suspend fun page(page: Int): AppResult<Page<Order>> = error("unused")
    }

    private companion object {
        fun failure(code: String): AppResult<Order> = AppResult.Failure(AppError(409, code, "failed", null))
        fun order(id: String) = Order(
            id, "NO-$id", "u1", "p1", "商品", "products/550e8400-e29b-41d4-a716-446655440000.png",
            10, OrderStatus.PENDING_PICKUP, 90, Instant.parse("2030-01-01T00:00:00Z"), null, null, null,
        )
    }
}
