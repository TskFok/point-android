package com.pointquest.android.feature.practice

import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.practice.PracticeRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrongQuestionsViewModelTest {
    @Test
    fun laterPagesAppendOnlyUnseenQuestionsAndUseServerMeta() = runBlocking {
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(
                immediate(page(1, 2, 3, wrong("q1"), wrong("q2"))),
                immediate(page(2, 2, 3, wrong("q2"), wrong("q3"))),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.load().join()

        viewModel.loadMore()?.join()

        assertEquals(listOf("q1", "q2", "q3"), viewModel.uiState.value.items.map { it.question.id })
        assertEquals(2, viewModel.uiState.value.paged.meta.page)
        assertEquals(3, viewModel.uiState.value.paged.meta.total)
        assertFalse(viewModel.uiState.value.canLoadMore)
    }

    @Test
    fun loadMoreDoesNotStartDuplicateRequestWhileOneIsRunning() = runBlocking {
        val deferred = CompletableDeferred<AppResult<Page<WrongQuestion>>>()
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(
                immediate(page(1, 2, 2, wrong("q1"))),
                DeferredPage(deferred),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.load().join()

        val first = viewModel.loadMore()
        val duplicate = viewModel.loadMore()

        assertNull(duplicate)
        assertEquals(listOf(1, 2), repository.pageCalls)
        deferred.complete(AppResult.Success(page(2, 2, 2, wrong("q2"))))
        first?.join()
        assertEquals(listOf("q1", "q2"), viewModel.uiState.value.items.map { it.question.id })
    }

    @Test
    fun responseAdjustmentReloadsLastServerPageOnlyOnce() = runBlocking {
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(
                immediate(page(pageNumber = 4, totalPages = 2, total = 3)),
                immediate(page(2, 2, 3, wrong("q3"))),
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.load().join()
        viewModel.loadingJob?.join()

        assertEquals(listOf(1, 2), repository.pageCalls)
        assertEquals(listOf("q3"), viewModel.uiState.value.items.map { it.question.id })
        assertEquals(2, viewModel.uiState.value.paged.meta.page)
    }

    @Test
    fun removingMasteredLastPageItemFallsBackToNewLastValidPage() = runBlocking {
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(
                immediate(page(1, 2, 2, wrong("q1"))),
                immediate(page(2, 2, 2, wrong("q2"))),
                immediate(page(1, 1, 1, wrong("q1"))),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.load().join()
        viewModel.loadMore()?.join()

        viewModel.removeMastered("q2")?.join()

        assertEquals(listOf(1, 2, 1), repository.pageCalls)
        assertEquals(listOf("q1"), viewModel.uiState.value.items.map { it.question.id })
        assertEquals(1, viewModel.uiState.value.paged.meta.total)
        assertEquals(1, viewModel.uiState.value.paged.meta.totalPages)
    }

    @Test
    fun emptyServerPageProducesEmptyStateWithoutLoadMore() = runBlocking {
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(immediate(page(1, 0, 0))),
        )
        val viewModel = viewModel(repository)

        viewModel.load().join()

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertTrue(viewModel.uiState.value.empty)
        assertFalse(viewModel.uiState.value.canLoadMore)
    }

    private fun viewModel(repository: PracticeRepository) = WrongQuestionsViewModel(
        repository,
        CoroutineScope(Job() + Dispatchers.Unconfined),
    )

    private sealed interface PageResponse

    private data class ImmediatePage(val value: AppResult<Page<WrongQuestion>>) : PageResponse

    private data class DeferredPage(
        val value: CompletableDeferred<AppResult<Page<WrongQuestion>>>,
    ) : PageResponse

    private class FakePracticeRepository(
        private val responses: ArrayDeque<PageResponse>,
    ) : PracticeRepository {
        val pageCalls = mutableListOf<Int>()

        override suspend fun wrongQuestions(page: Int): AppResult<Page<WrongQuestion>> {
            pageCalls += page
            return when (val response = responses.removeFirst()) {
                is ImmediatePage -> response.value
                is DeferredPage -> response.value.await()
            }
        }

        override suspend fun summary(): AppResult<PracticeSummary> = error("unused")
        override suspend fun nextQuestion(excludeIds: List<String>): AppResult<Question> = error("unused")
        override suspend fun answerFirst(questionId: String, selectedOptionId: String): AppResult<AnswerResult> =
            error("unused")
        override suspend fun answerWrong(questionId: String, selectedOptionId: String): AppResult<AnswerResult> =
            error("unused")
    }

    private companion object {
        fun page(
            pageNumber: Int,
            totalPages: Int,
            total: Int,
            vararg items: WrongQuestion,
        ) = Page(
            items = items.toList(),
            meta = PageMeta(pageNumber, pageSize = 1, total = total, totalPages = totalPages),
        )

        fun wrong(id: String) = WrongQuestion(
            errorCount = 1,
            firstAnsweredAt = Instant.parse("2030-01-01T00:00:00Z"),
            masteredAt = null,
            question = Question(id, "题干 $id", 5, emptyList()),
        )

        fun immediate(page: Page<WrongQuestion>) = ImmediatePage(AppResult.Success(page))
        fun <T> arrayDequeOf(vararg values: T) = ArrayDeque(values.toList())
    }
}
