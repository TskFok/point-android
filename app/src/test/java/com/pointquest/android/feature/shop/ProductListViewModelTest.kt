package com.pointquest.android.feature.shop

import com.pointquest.android.core.model.Page
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.auth.ActiveSession
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.points.PointsRepository
import com.pointquest.android.data.products.ProductsRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductListViewModelTest {
    @Test
    fun initializeLoadsBalanceAndExposesDeficitForUnaffordableProducts() = runTest {
        val products = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("cheap", pointsCost = 5), product("pricey", pointsCost = 20))))
        }
        val points = FakePointsRepository(balanceResult = AppResult.Success(10))
        val viewModel = ProductListViewModel(
            products,
            backgroundScope,
            pointsRepository = points,
        )
        viewModel.initialize()?.join()

        val state = viewModel.uiState.value
        assertEquals(10, state.balance)
        assertFalse(state.balanceFailed)
        assertNull(state.error)
        assertEquals(null, state.pointsDeficit(state.items[0]))
        assertEquals(10, state.pointsDeficit(state.items[1]))
        assertEquals(1, points.balanceCalls)
    }

    @Test
    fun balanceFailureDoesNotSetListErrorAndLeavesBalanceUnknown() = runTest {
        val products = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("p1"))))
        }
        val points = FakePointsRepository(balanceResult = failure("NETWORK_ERROR"))
        val viewModel = ProductListViewModel(
            products,
            backgroundScope,
            pointsRepository = points,
        )
        viewModel.initialize()?.join()

        val state = viewModel.uiState.value
        assertEquals(listOf("p1"), state.items.map(Product::id))
        assertNull(state.balance)
        assertTrue(state.balanceFailed)
        assertNull(state.error)
        assertNull(state.pointsDeficit(state.items.single()))
    }

    @Test
    fun appDataSyncBalanceOverridesLoadedBalance() = runTest {
        val products = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("p1", pointsCost = 10))))
        }
        val points = FakePointsRepository(balanceResult = AppResult.Success(20))
        val sync = signedInSync()
        val viewModel = ProductListViewModel(
            products,
            backgroundScope,
            appDataSync = sync,
            pointsRepository = points,
        )
        viewModel.initialize()?.join()
        assertEquals(20, viewModel.uiState.value.balance)

        sync.recordPracticeChanged(checkNotNull(sync.captureSession()), balance = 4)
        runCurrent()

        assertEquals(4, viewModel.uiState.value.balance)
        assertEquals(6, viewModel.uiState.value.pointsDeficit(viewModel.uiState.value.items.single()))
    }

    @Test
    fun searchDebouncesFor300msIgnoresDuplicatesAndResetsToFirstPage() = runTest {
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("initial"))))
            enqueuePage(success(page(1, 1, product("note"))))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope)
        viewModel.initialize()
        runCurrent()
        assertEquals(listOf(null to 1), repository.pageCalls)

        viewModel.updateSearch(" note ")
        advanceTimeBy(299)
        runCurrent()
        assertEquals(1, repository.pageCalls.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(null to 1, "note" to 1), repository.pageCalls)
        assertEquals(listOf("note"), viewModel.uiState.value.items.map(Product::id))

        viewModel.updateSearch("note")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(2, repository.pageCalls.size)
    }

    @Test
    fun whitespaceEquivalentToInitialEmptyQueryDoesNotReload() = runTest {
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("initial"))))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope)
        viewModel.initialize()?.join()

        viewModel.updateSearch("   ")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf(null to 1), repository.pageCalls)
    }

    @Test
    fun staleSearchResponseCannotReplaceTheNewestGeneration() = runTest {
        val old = CompletableDeferred<AppResult<Page<Product>>>()
        val newest = CompletableDeferred<AppResult<Page<Product>>>()
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("initial"))))
            enqueuePage(DeferredPage(old, ignoreCancellation = true))
            enqueuePage(DeferredPage(newest))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope)
        viewModel.initialize()
        runCurrent()

        viewModel.updateSearch("old")
        advanceTimeBy(300)
        runCurrent()
        viewModel.updateSearch("new")
        advanceTimeBy(300)
        runCurrent()

        newest.complete(success(page(1, 1, product("new"))))
        runCurrent()
        old.complete(success(page(1, 1, product("old"))))
        advanceUntilIdle()

        assertEquals(listOf("new"), viewModel.uiState.value.items.map(Product::id))
        assertEquals(listOf(null to 1, "old" to 1, "new" to 1), repository.pageCalls)
    }

    @Test
    fun loadMoreDeduplicatesIdsAndKeepsContentWhenFooterRequestFails() = runTest {
        val secondPage = CompletableDeferred<AppResult<Page<Product>>>()
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 3, product("p1"), product("p2"))))
            enqueuePage(DeferredPage(secondPage))
            enqueuePage(failure("NETWORK_ERROR"))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope)
        viewModel.initialize()
        runCurrent()

        val first = viewModel.loadMore()
        val duplicate = viewModel.loadMore()

        assertNull(duplicate)
        secondPage.complete(success(page(2, 3, product("p2"), product("p3"))))
        first?.join()
        assertEquals(listOf("p1", "p2", "p3"), viewModel.uiState.value.items.map(Product::id))

        viewModel.loadMore()?.join()

        assertEquals(listOf("p1", "p2", "p3"), viewModel.uiState.value.items.map(Product::id))
        assertFalse(viewModel.uiState.value.loadingMore)
        assertTrue(viewModel.uiState.value.loadMoreError != null)
        assertEquals(listOf(null to 1, null to 2, null to 3), repository.pageCalls)
    }

    @Test
    fun responsePageAdjustmentReloadsLastValidPageAndInitializationIsIdempotent() = runTest {
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(pageNumber = 4, totalPages = 2)))
            enqueuePage(success(page(2, 2, product("last"))))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope)

        viewModel.initialize()?.join()
        val duplicate = viewModel.initialize()

        assertNull(duplicate)
        assertEquals(listOf(null to 1, null to 2), repository.pageCalls)
        assertEquals(listOf("last"), viewModel.uiState.value.items.map(Product::id))
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun pullRefreshKeepsExistingProductsAndReportsFailureNonFatally() = runTest {
        val refreshResult = CompletableDeferred<AppResult<Page<Product>>>()
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("kept"))))
            enqueuePage(DeferredPage(refreshResult))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope)
        viewModel.initialize()?.join()

        val refresh = viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.uiState.value.refreshing)
        assertEquals(listOf("kept"), viewModel.uiState.value.items.map(Product::id))

        refreshResult.complete(failure("NETWORK_ERROR"))
        refresh?.join()

        assertFalse(viewModel.uiState.value.refreshing)
        assertEquals(listOf("kept"), viewModel.uiState.value.items.map(Product::id))
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.refreshError != null)
    }

    @Test
    fun inactiveProductIsRemovedAndShopRefreshesOnline() = runTest {
        val sync = signedInSync()
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("p1"), product("p2"))))
            enqueuePage(success(page(1, 1, product("p2"))))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope, appDataSync = sync)
        viewModel.initialize()?.join()

        sync.recordProductInactive(checkNotNull(sync.captureSession()), "p1")
        runCurrent()
        viewModel.refreshJob?.join()

        assertEquals(listOf("p2"), viewModel.uiState.value.items.map(Product::id))
        assertEquals(listOf(null to 1, null to 1), repository.pageCalls)
    }

    @Test
    fun successfulFirstPageAfterInactiveRevisionClearsTombstoneAndShowsRelistedProduct() = runTest {
        val authoritativeRefresh = CompletableDeferred<AppResult<Page<Product>>>()
        val sync = signedInSync()
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("p1"), product("p2"))))
            enqueuePage(DeferredPage(authoritativeRefresh))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope, appDataSync = sync)
        viewModel.initialize()?.join()

        sync.recordProductInactive(checkNotNull(sync.captureSession()), "p1")
        runCurrent()
        assertEquals(listOf("p2"), viewModel.uiState.value.items.map(Product::id))

        authoritativeRefresh.complete(success(page(1, 1, product("p1"), product("p2"))))
        viewModel.refreshJob?.join()

        assertEquals(listOf("p1", "p2"), viewModel.uiState.value.items.map(Product::id))
        assertTrue(sync.state.value.inactiveProductIds.isEmpty())
    }

    @Test
    fun failedAuthoritativeRefreshKeepsTombstoneUntilSuccessfulRetry() = runTest {
        val sync = signedInSync()
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("p1"), product("p2"))))
            enqueuePage(failure("NETWORK_ERROR"))
            enqueuePage(success(page(1, 1, product("p1"), product("p2"))))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope, appDataSync = sync)
        viewModel.initialize()?.join()

        sync.recordProductInactive(checkNotNull(sync.captureSession()), "p1")
        runCurrent()
        viewModel.refreshJob?.join()
        assertEquals(listOf("p2"), viewModel.uiState.value.items.map(Product::id))
        assertTrue("p1" in sync.state.value.inactiveProductIds)

        viewModel.refresh()?.join()

        assertEquals(listOf("p1", "p2"), viewModel.uiState.value.items.map(Product::id))
        assertTrue(sync.state.value.inactiveProductIds.isEmpty())
    }

    @Test
    fun shopRevisionDuringInitialLoadRestartsAndOldResponseCannotReplaceLatest() = runTest {
        val oldInitial = CompletableDeferred<AppResult<Page<Product>>>()
        val latest = CompletableDeferred<AppResult<Page<Product>>>()
        val sync = signedInSync()
        val repository = FakeProductsRepository().apply {
            enqueuePage(DeferredPage(oldInitial, ignoreCancellation = true))
            enqueuePage(DeferredPage(latest))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope, appDataSync = sync)
        viewModel.initialize()
        runCurrent()

        sync.recordOrderCreated(checkNotNull(sync.captureSession()), balance = 30)
        runCurrent()
        assertEquals(listOf(null to 1, null to 1), repository.pageCalls)

        latest.complete(success(page(1, 1, product("latest"))))
        runCurrent()
        oldInitial.complete(success(page(1, 1, product("old"))))
        advanceUntilIdle()

        assertEquals(listOf("latest"), viewModel.uiState.value.items.map(Product::id))
    }

    @Test
    fun inactiveRevisionDuringPullRefreshRestartsAndOldResponseCannotReplaceLatest() = runTest {
        val oldRefresh = CompletableDeferred<AppResult<Page<Product>>>()
        val latest = CompletableDeferred<AppResult<Page<Product>>>()
        val sync = signedInSync()
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("p1"))))
            enqueuePage(DeferredPage(oldRefresh, ignoreCancellation = true))
            enqueuePage(DeferredPage(latest))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope, appDataSync = sync)
        viewModel.initialize()?.join()
        viewModel.refresh()
        runCurrent()

        sync.recordProductInactive(checkNotNull(sync.captureSession()), "p1")
        runCurrent()
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(listOf(null to 1, null to 1, null to 1), repository.pageCalls)

        latest.complete(success(page(1, 1, product("latest"))))
        runCurrent()
        oldRefresh.complete(success(page(1, 1, product("old"))))
        advanceUntilIdle()

        assertEquals(listOf("latest"), viewModel.uiState.value.items.map(Product::id))
    }

    private sealed interface PageResponse
    private data class ImmediatePage(val value: AppResult<Page<Product>>) : PageResponse
    private data class DeferredPage(
        val deferred: CompletableDeferred<AppResult<Page<Product>>>,
        val ignoreCancellation: Boolean = false,
    ) : PageResponse

    private class FakeProductsRepository : ProductsRepository {
        val pageCalls = mutableListOf<Pair<String?, Int>>()
        private val responses = ArrayDeque<PageResponse>()

        fun enqueuePage(result: AppResult<Page<Product>>) {
            responses += ImmediatePage(result)
        }

        fun enqueuePage(response: PageResponse) {
            responses += response
        }

        override suspend fun page(search: String?, page: Int): AppResult<Page<Product>> {
            pageCalls += search to page
            return when (val response = responses.removeFirst()) {
                is ImmediatePage -> response.value
                is DeferredPage -> if (response.ignoreCancellation) {
                    withContext(NonCancellable) { response.deferred.await() }
                } else {
                    response.deferred.await()
                }
            }
        }

        override suspend fun detail(id: String): AppResult<Product> = error("unused")
    }

    private class FakePointsRepository(
        var balanceResult: AppResult<Int> = AppResult.Success(0),
    ) : PointsRepository {
        var balanceCalls = 0
        override suspend fun balance(): AppResult<Int> {
            balanceCalls++
            return balanceResult
        }
        override suspend fun ledger(page: Int): AppResult<Page<PointLedgerEntry>> = error("unused")
    }

    private companion object {
        fun success(page: Page<Product>) = AppResult.Success(page)
        fun failure(code: String) = AppResult.Failure(AppError(null, code, "failed", null))
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
        fun page(
            pageNumber: Int,
            totalPages: Int,
            vararg products: Product,
        ) = Page(
            products.toList(),
            PageMeta(pageNumber, pageSize = 2, total = totalPages * 2, totalPages = totalPages),
        )

        fun product(id: String, pointsCost: Int = 10) = Product(
            id = id,
            name = "商品 $id",
            description = "描述",
            imageKey = "products/550e8400-e29b-41d4-a716-446655440000.png",
            pointsCost = pointsCost,
            stock = 2,
            isActive = true,
            createdAt = Instant.parse("2030-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2030-01-01T00:00:00Z"),
        )
    }
}
