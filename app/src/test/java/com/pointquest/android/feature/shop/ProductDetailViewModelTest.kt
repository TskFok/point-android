package com.pointquest.android.feature.shop

import com.pointquest.android.R
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.auth.ActiveSession
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.OrderStatus
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.orders.OrdersRepository
import com.pointquest.android.data.points.PointsRepository
import com.pointquest.android.data.products.ProductsRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductDetailViewModelTest {
    @Test
    fun detailAndBalanceStartConcurrentlyAndRepeatedInitializationDoesNothing() = runTest {
        val productResult = CompletableDeferred<AppResult<Product>>()
        val balanceResult = CompletableDeferred<AppResult<Int>>()
        val products = FakeProductsRepository(productDeferred = productResult)
        val points = FakePointsRepository(balanceDeferred = balanceResult)
        val viewModel = ProductDetailViewModel("p1", products, FakeOrdersRepository(), points, backgroundScope)

        viewModel.initialize()
        runCurrent()

        assertTrue(products.detailStarted)
        assertTrue(points.balanceStarted)
        productResult.complete(AppResult.Success(product()))
        balanceResult.complete(AppResult.Success(20))
        viewModel.loadingJob?.join()

        assertEquals(product(), viewModel.uiState.value.product)
        assertEquals(20, viewModel.uiState.value.balance)
        assertTrue(viewModel.uiState.value.canRedeem)
        assertNull(viewModel.initialize())
        assertEquals(1, products.detailCalls)
        assertEquals(1, points.balanceCalls)
    }

    @Test
    fun balanceFailureKeepsProductAndRetryOnlyReloadsBalance() = runTest {
        val products = FakeProductsRepository(detailResult = AppResult.Success(product()))
        val points = FakePointsRepository(balanceResult = failure("NETWORK_ERROR"))
        val viewModel = ProductDetailViewModel("p1", products, FakeOrdersRepository(), points, backgroundScope)
        viewModel.initialize()?.join()

        assertEquals(product(), viewModel.uiState.value.product)
        assertNull(viewModel.uiState.value.balance)
        assertFalse(viewModel.uiState.value.canRedeem)
        assertEquals(UiText.Resource(R.string.product_balance_load_failed), viewModel.uiState.value.error)

        points.balanceResult = AppResult.Success(25)
        viewModel.retry()?.join()

        assertEquals(1, products.detailCalls)
        assertEquals(2, points.balanceCalls)
        assertEquals(25, viewModel.uiState.value.balance)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun outOfStockSetsStockToZeroAndDisablesRedeem() = runTest {
        val orders = FakeOrdersRepository().apply { enqueue(failure("OUT_OF_STOCK")) }
        val viewModel = loadedViewModel(orders)

        viewModel.requestRedeemConfirmation()
        viewModel.confirmRedeem()?.join()

        assertEquals(0, viewModel.uiState.value.product?.stock)
        assertFalse(viewModel.uiState.value.canRedeem)
        assertEquals(UiText.Resource(R.string.product_out_of_stock), viewModel.uiState.value.message)
    }

    @Test
    fun insufficientPointsAcceptsOnlySafeIntegralBalanceDetail() = runTest {
        val validOrders = FakeOrdersRepository().apply {
            enqueue(failure("INSUFFICIENT_POINTS", mapOf("balance" to 7.0)))
        }
        val valid = loadedViewModel(validOrders)
        valid.requestRedeemConfirmation()
        valid.confirmRedeem()?.join()
        assertEquals(7, valid.uiState.value.balance)
        assertEquals(3, valid.uiState.value.pointsDeficit)
        assertFalse(valid.uiState.value.canRedeem)

        val invalidOrders = FakeOrdersRepository().apply {
            enqueue(failure("INSUFFICIENT_POINTS", mapOf("balance" to Double.NaN)))
        }
        val invalid = loadedViewModel(invalidOrders)
        invalid.requestRedeemConfirmation()
        invalid.confirmRedeem()?.join()
        assertNull(invalid.uiState.value.balance)
        assertFalse(invalid.uiState.value.canRedeem)
    }

    @Test
    fun insufficientPointsRejectsOutOfRangeNumbersAndAcceptsExactSafeBoundaries() = runTest {
        val rejectedBalances = listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            -1f,
            1.5f,
            2_147_483_648f,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            -1.0,
            1.5,
            2_147_483_648.0,
            Int.MAX_VALUE.toLong() + 1L,
        )
        rejectedBalances.forEach { reportedBalance ->
            val orders = FakeOrdersRepository().apply {
                enqueue(failure("INSUFFICIENT_POINTS", mapOf("balance" to reportedBalance)))
            }
            val viewModel = loadedViewModel(orders)

            viewModel.requestRedeemConfirmation()
            viewModel.confirmRedeem()?.join()

            assertNull("应拒绝越界余额：$reportedBalance", viewModel.uiState.value.balance)
            assertFalse(viewModel.uiState.value.canRedeem)
        }

        val acceptedBalances = listOf(
            2_147_483_520f to 2_147_483_520,
            Int.MAX_VALUE.toDouble() to Int.MAX_VALUE,
            Int.MAX_VALUE.toLong() to Int.MAX_VALUE,
        )
        acceptedBalances.forEach { (reportedBalance, expected) ->
            val orders = FakeOrdersRepository().apply {
                enqueue(failure("INSUFFICIENT_POINTS", mapOf("balance" to reportedBalance)))
            }
            val viewModel = loadedViewModel(orders)

            viewModel.requestRedeemConfirmation()
            viewModel.confirmRedeem()?.join()

            assertEquals(expected, viewModel.uiState.value.balance)
        }
    }

    @Test
    fun inactiveProductEmitsReturnEventAndConflictAllowsAUserInitiatedNewOperation() = runTest {
        val inactiveOrders = FakeOrdersRepository().apply { enqueue(failure("PRODUCT_INACTIVE")) }
        val inactive = loadedViewModel(inactiveOrders)
        inactive.requestRedeemConfirmation()
        inactive.confirmRedeem()?.join()

        assertFalse(inactive.uiState.value.product?.isActive ?: true)
        assertEquals(ProductDetailEvent.ReturnToShop, inactive.events.first())

        val conflictOrders = FakeOrdersRepository().apply {
            enqueue(failure("IDEMPOTENCY_CONFLICT"))
            enqueue(AppResult.Success(order("o2")))
        }
        val conflict = loadedViewModel(conflictOrders)
        conflict.requestRedeemConfirmation()
        conflict.confirmRedeem()?.join()
        assertTrue(conflict.uiState.value.canRedeem)
        assertEquals(UiText.Resource(R.string.product_redeem_conflict), conflict.uiState.value.message)

        conflict.requestRedeemConfirmation()
        val event = async { conflict.events.first() }
        conflict.confirmRedeem()?.join()

        assertEquals(ProductDetailEvent.NavigateToOrder("o2"), event.await())
        assertEquals(2, conflictOrders.redeemCalls)
    }

    @Test
    fun redeemIsMutuallyExclusiveWhileRequestIsRunning() = runTest {
        val redeemResult = CompletableDeferred<AppResult<Order>>()
        val orders = FakeOrdersRepository().apply { enqueue(redeemResult) }
        val viewModel = loadedViewModel(orders)
        viewModel.requestRedeemConfirmation()

        val first = viewModel.confirmRedeem()
        val duplicate = viewModel.confirmRedeem()
        runCurrent()

        assertNull(duplicate)
        assertEquals(1, orders.redeemCalls)
        redeemResult.complete(AppResult.Success(order("o1")))
        first?.join()
    }

    @Test
    fun failedRedeemKeepsConfirmationAndReusesOperationKeyOnRetry() = runTest {
        val orders = FakeOrdersRepository().apply {
            enqueue(failure("NETWORK_ERROR"))
            enqueue(AppResult.Success(order("o1")))
        }
        val viewModel = loadedViewModel(orders)

        viewModel.requestRedeemConfirmation()
        viewModel.confirmRedeem()?.join()

        assertTrue(viewModel.uiState.value.showRedeemConfirmation)
        assertTrue(viewModel.uiState.value.redeemRetryPending)
        assertNotNull(orders.redeemKeys.single())
        val firstKey = orders.redeemKeys.single()

        viewModel.confirmRedeem()?.join()

        assertEquals(listOf(firstKey, firstKey), orders.redeemKeys)
        assertFalse(viewModel.uiState.value.redeemRetryPending)
    }

    @Test
    fun dismissAfterRetryableFailureClearsRetryPending() = runTest {
        val orders = FakeOrdersRepository().apply { enqueue(failure("NETWORK_ERROR")) }
        val viewModel = loadedViewModel(orders)
        viewModel.requestRedeemConfirmation()
        viewModel.confirmRedeem()?.join()
        assertTrue(viewModel.uiState.value.redeemRetryPending)

        viewModel.dismissRedeemConfirmation()

        assertFalse(viewModel.uiState.value.showRedeemConfirmation)
        assertFalse(viewModel.uiState.value.redeemRetryPending)
    }

    @Test
    fun redeemAndInactiveResultsSynchronizeExistingTopLevelPages() = runTest {
        val successSync = signedInSync()
        val successOrders = FakeOrdersRepository().apply { enqueue(AppResult.Success(order("o1"))) }
        val success = ProductDetailViewModel(
            "p1",
            FakeProductsRepository(detailResult = AppResult.Success(product())),
            successOrders,
            FakePointsRepository(balanceResult = AppResult.Success(20)),
            scopeOverride = backgroundScope,
            appDataSync = successSync,
        )
        success.initialize()?.join()
        success.requestRedeemConfirmation()
        success.confirmRedeem()?.join()
        assertEquals(10, successSync.state.value.balance)
        assertEquals(1L, successSync.state.value.homeRefreshRevision)
        assertEquals(1L, successSync.state.value.shopRefreshRevision)

        val inactiveSync = signedInSync()
        val inactiveOrders = FakeOrdersRepository().apply { enqueue(failure("PRODUCT_INACTIVE")) }
        val inactive = ProductDetailViewModel(
            "p1",
            FakeProductsRepository(detailResult = AppResult.Success(product())),
            inactiveOrders,
            FakePointsRepository(balanceResult = AppResult.Success(20)),
            scopeOverride = backgroundScope,
            appDataSync = inactiveSync,
        )
        inactive.initialize()?.join()
        inactive.requestRedeemConfirmation()
        inactive.confirmRedeem()?.join()
        assertTrue("p1" in inactiveSync.state.value.inactiveProductIds)
    }

    @Test
    fun successfulRedeemAcrossAuthRefreshMustStillPublishTopLevelInvalidation() = runTest {
        val sessionState = SessionState().apply {
            publish(
                ActiveSession(
                    user = User("student-1", "student", UserRole.STUDENT, 42),
                    accessToken = "old-token",
                    accessTokenExpiresAt = Instant.parse("2030-01-01T00:05:00Z"),
                    generation = 1,
                    loginSessionId = 1,
                ),
            )
        }
        val sync = AppDataSync(sessionState)
        val orders = FakeOrdersRepository().apply {
            beforeRedeem = {
                sessionState.publish(
                    ActiveSession(
                        user = User("student-1", "student", UserRole.STUDENT, 42),
                        accessToken = "refreshed-token",
                        accessTokenExpiresAt = Instant.parse("2030-01-01T01:05:00Z"),
                        generation = 2,
                        loginSessionId = 1,
                    ),
                )
            }
            enqueue(AppResult.Success(order("o1")))
        }
        val viewModel = ProductDetailViewModel(
            "p1",
            FakeProductsRepository(detailResult = AppResult.Success(product())),
            orders,
            FakePointsRepository(balanceResult = AppResult.Success(20)),
            scopeOverride = backgroundScope,
            appDataSync = sync,
        )
        viewModel.initialize()?.join()
        viewModel.requestRedeemConfirmation()

        viewModel.confirmRedeem()?.join()

        assertEquals(10, sync.state.value.balance)
        assertEquals(1L, sync.state.value.homeRefreshRevision)
        assertEquals(1L, sync.state.value.shopRefreshRevision)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.loadedViewModel(
        orders: FakeOrdersRepository,
    ): ProductDetailViewModel {
        val viewModel = ProductDetailViewModel(
            "p1",
            FakeProductsRepository(detailResult = AppResult.Success(product())),
            orders,
            FakePointsRepository(balanceResult = AppResult.Success(20)),
            backgroundScope,
        )
        viewModel.initialize()?.join()
        return viewModel
    }

    private class FakeProductsRepository(
        private val productDeferred: CompletableDeferred<AppResult<Product>>? = null,
        var detailResult: AppResult<Product> = AppResult.Success(product()),
    ) : ProductsRepository {
        var detailCalls = 0
        var detailStarted = false
        override suspend fun detail(id: String): AppResult<Product> {
            detailCalls++
            detailStarted = true
            return productDeferred?.await() ?: detailResult
        }
        override suspend fun page(search: String?, page: Int): AppResult<Page<Product>> = error("unused")
    }

    private class FakePointsRepository(
        private val balanceDeferred: CompletableDeferred<AppResult<Int>>? = null,
        var balanceResult: AppResult<Int> = AppResult.Success(20),
    ) : PointsRepository {
        var balanceCalls = 0
        var balanceStarted = false
        override suspend fun balance(): AppResult<Int> {
            balanceCalls++
            balanceStarted = true
            return balanceDeferred?.await() ?: balanceResult
        }
        override suspend fun ledger(page: Int): AppResult<Page<PointLedgerEntry>> = error("unused")
    }

    private class FakeOrdersRepository : OrdersRepository {
        private val responses = ArrayDeque<Any>()
        var redeemCalls = 0
        val redeemKeys = mutableListOf<String?>()
        var beforeRedeem: (suspend () -> Unit)? = null
        fun enqueue(result: AppResult<Order>) { responses += result }
        fun enqueue(result: CompletableDeferred<AppResult<Order>>) { responses += result }
        override suspend fun redeem(productId: String, idempotencyKey: String?): AppResult<Order> {
            redeemCalls++
            redeemKeys += idempotencyKey
            beforeRedeem?.invoke()
            return when (val response = responses.removeFirst()) {
                is CompletableDeferred<*> -> @Suppress("UNCHECKED_CAST")
                (response as CompletableDeferred<AppResult<Order>>).await()
                else -> @Suppress("UNCHECKED_CAST") (response as AppResult<Order>)
            }
        }
        override suspend fun page(page: Int): AppResult<Page<Order>> = error("unused")
        override suspend fun detail(id: String): AppResult<Order> = error("unused")
    }

    private companion object {
        fun failure(code: String, details: Map<String, Any?> = emptyMap()) =
            AppResult.Failure(AppError(409, code, "failed", null, details))

        fun signedInSync(): AppDataSync {
            val sessionState = SessionState().apply {
                publish(
                    ActiveSession(
                        user = User("student-1", "student", UserRole.STUDENT, 42),
                        accessToken = "token",
                        accessTokenExpiresAt = Instant.parse("2030-01-01T00:05:00Z"),
                        generation = 1,
                        loginSessionId = 1,
                    ),
                )
            }
            return AppDataSync(sessionState)
        }

        fun product() = Product(
            "p1", "笔记本", "描述", "products/550e8400-e29b-41d4-a716-446655440000.png",
            10, 2, true, Instant.parse("2030-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"),
        )

        fun order(id: String) = Order(
            id, "NO-$id", "u1", "p1", "笔记本", "products/550e8400-e29b-41d4-a716-446655440000.png",
            10, OrderStatus.PENDING_PICKUP, 10, Instant.parse("2030-01-01T00:00:00Z"), null, null, null,
        )
    }
}
