package com.pointquest.android.feature.points

import com.pointquest.android.R
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PointLedgerType
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.points.PointsRepository
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
class PointsViewModelTest {
    @Test
    fun balanceAndFirstLedgerPageLoadConcurrentlyAndInitializationIsIdempotent() = runTest {
        val balance = CompletableDeferred<AppResult<Int>>()
        val ledger = CompletableDeferred<AppResult<Page<PointLedgerEntry>>>()
        val repository = FakePointsRepository(balanceDeferred = balance).apply { enqueue(ledger) }
        val viewModel = PointsViewModel(repository, backgroundScope)

        viewModel.initialize()
        runCurrent()
        assertTrue(repository.balanceStarted)
        assertEquals(listOf(1), repository.pageCalls)

        balance.complete(AppResult.Success(88))
        ledger.complete(success(page(1, 1, entry("e1"))))
        viewModel.loadingJob?.join()

        assertEquals(88, viewModel.uiState.value.balance)
        assertEquals(listOf("e1"), viewModel.uiState.value.items.map(PointLedgerEntry::id))
        assertNull(viewModel.initialize())
        assertEquals(1, repository.balanceCalls)
    }

    @Test
    fun balanceFailureKeepsLedgerAndRetryDoesNotReloadSuccessfulPage() = runTest {
        val repository = FakePointsRepository(balanceResult = failure("NETWORK_ERROR")).apply {
            enqueue(success(page(1, 1, entry("e1"))))
        }
        val viewModel = PointsViewModel(repository, backgroundScope)
        viewModel.initialize()?.join()

        assertEquals(listOf("e1"), viewModel.uiState.value.items.map(PointLedgerEntry::id))
        assertEquals(UiText.Resource(R.string.points_balance_load_failed), viewModel.uiState.value.error)

        repository.balanceResult = AppResult.Success(50)
        viewModel.retry()?.join()

        assertEquals(2, repository.balanceCalls)
        assertEquals(listOf(1), repository.pageCalls)
        assertEquals(50, viewModel.uiState.value.balance)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun loadMoreIsExclusiveDeduplicatesAndKeepsContentOnFooterFailure() = runTest {
        val pending = CompletableDeferred<AppResult<Page<PointLedgerEntry>>>()
        val repository = FakePointsRepository(balanceResult = AppResult.Success(20)).apply {
            enqueue(success(page(1, 3, entry("e1"), entry("e2"))))
            enqueue(pending)
            enqueue(failure("NETWORK_ERROR"))
        }
        val viewModel = PointsViewModel(repository, backgroundScope)
        viewModel.initialize()?.join()

        val first = viewModel.loadMore()
        val duplicate = viewModel.loadMore()
        runCurrent()
        assertNull(duplicate)
        pending.complete(success(page(2, 3, entry("e2"), entry("e3"))))
        first?.join()
        viewModel.loadMore()?.join()

        assertEquals(listOf("e1", "e2", "e3"), viewModel.uiState.value.items.map(PointLedgerEntry::id))
        assertTrue(viewModel.uiState.value.loadMoreError != null)
        assertFalse(viewModel.uiState.value.loadingMore)
    }

    private class FakePointsRepository(
        private val balanceDeferred: CompletableDeferred<AppResult<Int>>? = null,
        var balanceResult: AppResult<Int> = AppResult.Success(20),
    ) : PointsRepository {
        private val responses = ArrayDeque<Any>()
        val pageCalls = mutableListOf<Int>()
        var balanceCalls = 0
        var balanceStarted = false
        fun enqueue(result: AppResult<Page<PointLedgerEntry>>) { responses += result }
        fun enqueue(result: CompletableDeferred<AppResult<Page<PointLedgerEntry>>>) { responses += result }
        override suspend fun balance(): AppResult<Int> {
            balanceCalls++
            balanceStarted = true
            return balanceDeferred?.await() ?: balanceResult
        }
        override suspend fun ledger(page: Int): AppResult<Page<PointLedgerEntry>> {
            pageCalls += page
            return when (val response = responses.removeFirst()) {
                is CompletableDeferred<*> -> @Suppress("UNCHECKED_CAST")
                (response as CompletableDeferred<AppResult<Page<PointLedgerEntry>>>).await()
                else -> @Suppress("UNCHECKED_CAST") (response as AppResult<Page<PointLedgerEntry>>)
            }
        }
    }

    private companion object {
        fun <T> failure(code: String): AppResult<T> = AppResult.Failure(AppError(null, code, "failed", null))
        fun success(page: Page<PointLedgerEntry>) = AppResult.Success(page)
        fun page(number: Int, totalPages: Int, vararg entries: PointLedgerEntry) = Page(
            entries.toList(), PageMeta(number, 2, totalPages * 2, totalPages),
        )
        fun entry(id: String) = PointLedgerEntry(
            id, "u1", PointLedgerType.ANSWER_REWARD, 5, 20, "a1", null,
            Instant.parse("2030-01-01T00:00:00Z"),
        )
    }
}
