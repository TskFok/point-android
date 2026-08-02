package com.pointquest.android.feature.shop

import com.pointquest.android.core.model.Page
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
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
        val sync = AppDataSync()
        val repository = FakeProductsRepository().apply {
            enqueuePage(success(page(1, 1, product("p1"), product("p2"))))
            enqueuePage(success(page(1, 1, product("p2"))))
        }
        val viewModel = ProductListViewModel(repository, backgroundScope, appDataSync = sync)
        viewModel.initialize()?.join()

        sync.recordProductInactive("p1")
        runCurrent()
        viewModel.refreshJob?.join()

        assertEquals(listOf("p2"), viewModel.uiState.value.items.map(Product::id))
        assertEquals(listOf(null to 1, null to 1), repository.pageCalls)
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

    private companion object {
        fun success(page: Page<Product>) = AppResult.Success(page)
        fun failure(code: String) = AppResult.Failure(AppError(null, code, "failed", null))
        fun page(
            pageNumber: Int,
            totalPages: Int,
            vararg products: Product,
        ) = Page(
            products.toList(),
            PageMeta(pageNumber, pageSize = 2, total = totalPages * 2, totalPages = totalPages),
        )

        fun product(id: String) = Product(
            id = id,
            name = "商品 $id",
            description = "描述",
            imageKey = "products/550e8400-e29b-41d4-a716-446655440000.png",
            pointsCost = 10,
            stock = 2,
            isActive = true,
            createdAt = Instant.parse("2030-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2030-01-01T00:00:00Z"),
        )
    }
}
