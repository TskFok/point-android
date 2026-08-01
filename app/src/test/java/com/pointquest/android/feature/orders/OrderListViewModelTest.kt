package com.pointquest.android.feature.orders

import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.OrderStatus
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.orders.OrdersRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderListViewModelTest {
    @Test
    fun paginationDeduplicatesOrdersAndRepeatedInitializationDoesNotReload() = runTest {
        val repository = FakeOrdersRepository().apply {
            enqueue(success(page(1, 2, order("o1"), order("o2"))))
            enqueue(success(page(2, 2, order("o2"), order("o3"))))
        }
        val viewModel = OrderListViewModel(repository, backgroundScope)
        viewModel.initialize()?.join()
        viewModel.loadMore()?.join()

        assertNull(viewModel.initialize())
        assertEquals(listOf(1, 2), repository.pageCalls)
        assertEquals(listOf("o1", "o2", "o3"), viewModel.uiState.value.items.map(Order::id))
    }

    @Test
    fun loadMoreIsExclusiveAndFailureKeepsExistingContentForFooterRetry() = runTest {
        val pending = CompletableDeferred<AppResult<Page<Order>>>()
        val repository = FakeOrdersRepository().apply {
            enqueue(success(page(1, 3, order("o1"))))
            enqueue(pending)
            enqueue(failure("NETWORK_ERROR"))
        }
        val viewModel = OrderListViewModel(repository, backgroundScope)
        viewModel.initialize()?.join()

        val first = viewModel.loadMore()
        val duplicate = viewModel.loadMore()
        runCurrent()
        assertNull(duplicate)

        pending.complete(success(page(2, 3, order("o2"))))
        first?.join()
        viewModel.loadMore()?.join()

        assertEquals(listOf("o1", "o2"), viewModel.uiState.value.items.map(Order::id))
        assertTrue(viewModel.uiState.value.loadMoreError != null)
        assertFalse(viewModel.uiState.value.loadingMore)
        assertEquals(listOf(1, 2, 3), repository.pageCalls)
    }

    private class FakeOrdersRepository : OrdersRepository {
        private val responses = ArrayDeque<Any>()
        val pageCalls = mutableListOf<Int>()
        fun enqueue(result: AppResult<Page<Order>>) { responses += result }
        fun enqueue(result: CompletableDeferred<AppResult<Page<Order>>>) { responses += result }
        override suspend fun page(page: Int): AppResult<Page<Order>> {
            pageCalls += page
            return when (val response = responses.removeFirst()) {
                is CompletableDeferred<*> -> @Suppress("UNCHECKED_CAST")
                (response as CompletableDeferred<AppResult<Page<Order>>>).await()
                else -> @Suppress("UNCHECKED_CAST") (response as AppResult<Page<Order>>)
            }
        }
        override suspend fun detail(id: String): AppResult<Order> = error("unused")
        override suspend fun redeem(productId: String): AppResult<Order> = error("unused")
    }

    private companion object {
        fun success(page: Page<Order>) = AppResult.Success(page)
        fun failure(code: String) = AppResult.Failure(AppError(null, code, "failed", null))
        fun page(number: Int, totalPages: Int, vararg orders: Order) = Page(
            orders.toList(), PageMeta(number, 2, totalPages * 2, totalPages),
        )
        fun order(id: String) = Order(
            id, "NO-$id", "u1", "p1", "商品", "products/550e8400-e29b-41d4-a716-446655440000.png",
            10, OrderStatus.PENDING_PICKUP, 90, Instant.parse("2030-01-01T00:00:00Z"), null, null, null,
        )
    }
}
