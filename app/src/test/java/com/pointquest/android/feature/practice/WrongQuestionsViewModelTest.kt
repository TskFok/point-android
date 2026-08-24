package com.pointquest.android.feature.practice

import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.practice.PracticeRepository
import com.pointquest.android.test.FakeLearnerLanguageStore
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
    fun initialLoadUsesCurrentLearnerLanguage() = runBlocking {
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(immediate(page(1, 1, 1, wrong("q1")))),
        )
        val viewModel = viewModel(
            repository = repository,
            store = FakeLearnerLanguageStore(LearnerLanguage.FR),
        )

        viewModel.load().join()

        assertEquals(listOf(PageRequest(1, LearnerLanguage.FR)), repository.requests)
        assertEquals(LearnerLanguage.FR, viewModel.uiState.value.language)
    }

    @Test
    fun repeatedInitializationKeepsLoadedPagesWithoutAnotherRepositoryCall() = runBlocking {
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(
                immediate(page(1, 2, 2, wrong("q1"))),
                immediate(page(2, 2, 2, wrong("q2"))),
            ),
        )
        val viewModel = viewModel(repository = repository)
        viewModel.initialize()?.join()
        viewModel.loadMore()?.join()

        val duplicate = viewModel.initialize()

        assertNull(duplicate)
        assertEquals(listOf(1, 2), repository.requests.map { it.page })
        assertEquals(listOf("q1", "q2"), viewModel.uiState.value.items.map { it.question.id })
        assertEquals(2, viewModel.uiState.value.paged.meta.page)
    }

    @Test
    fun laterPagesAppendOnlyUnseenQuestionsAndUseServerMeta() = runBlocking {
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(
                immediate(page(1, 2, 3, wrong("q1"), wrong("q2"))),
                immediate(page(2, 2, 3, wrong("q2"), wrong("q3"))),
            ),
        )
        val viewModel = viewModel(repository = repository)
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
        val viewModel = viewModel(repository = repository)
        viewModel.load().join()

        val first = viewModel.loadMore()
        val duplicate = viewModel.loadMore()

        assertNull(duplicate)
        assertEquals(listOf(1, 2), repository.requests.map { it.page })
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
        val viewModel = viewModel(repository = repository)

        viewModel.load().join()
        viewModel.loadingJob?.join()

        assertEquals(listOf(1, 2), repository.requests.map { it.page })
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
        val viewModel = viewModel(repository = repository)
        viewModel.load().join()
        viewModel.loadMore()?.join()

        viewModel.removeMastered("q2")?.join()

        assertEquals(listOf(1, 2, 1), repository.requests.map { it.page })
        assertEquals(listOf("q1"), viewModel.uiState.value.items.map { it.question.id })
        assertEquals(1, viewModel.uiState.value.paged.meta.total)
        assertEquals(1, viewModel.uiState.value.paged.meta.totalPages)
    }

    @Test
    fun emptyServerPageProducesEmptyStateWithoutLoadMore() = runBlocking {
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(immediate(page(1, 0, 0))),
        )
        val viewModel = viewModel(repository = repository)

        viewModel.load().join()

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertTrue(viewModel.uiState.value.empty)
        assertFalse(viewModel.uiState.value.canLoadMore)
    }

    @Test
    fun changingLanguageResetsPagingAndUsesNewLanguageForRefreshAndLoadMore() = runBlocking {
        val firstJaPage = CompletableDeferred<AppResult<Page<WrongQuestion>>>()
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(
                immediate(page(1, 2, 2, wrong("q1"))),
                immediate(page(2, 2, 2, wrong("q2"))),
                DeferredPage(firstJaPage),
                immediate(page(2, 2, 2, wrong("jq2"))),
            ),
        )
        val store = FakeLearnerLanguageStore(LearnerLanguage.ALL)
        val viewModel = viewModel(repository = repository, store = store)
        viewModel.load().join()
        viewModel.loadMore()?.join()

        store.setLanguage(LearnerLanguage.JA)

        assertEquals(
            listOf(
                PageRequest(1, LearnerLanguage.ALL),
                PageRequest(2, LearnerLanguage.ALL),
                PageRequest(1, LearnerLanguage.JA),
            ),
            repository.requests,
        )
        assertEquals(LearnerLanguage.JA, viewModel.uiState.value.language)
        assertTrue(viewModel.uiState.value.loading)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(1, viewModel.uiState.value.paged.meta.page)

        firstJaPage.complete(AppResult.Success(page(1, 2, 2, wrong("jq1"))))
        viewModel.loadingJob?.join()
        viewModel.loadMore()?.join()

        assertEquals(
            listOf(
                PageRequest(1, LearnerLanguage.ALL),
                PageRequest(2, LearnerLanguage.ALL),
                PageRequest(1, LearnerLanguage.JA),
                PageRequest(2, LearnerLanguage.JA),
            ),
            repository.requests,
        )
        assertEquals(listOf("jq1", "jq2"), viewModel.uiState.value.items.map { it.question.id })
        assertEquals(LearnerLanguage.JA, viewModel.uiState.value.language)
    }

    @Test
    fun staleLoadMoreResultDoesNotPolluteNewLanguageState() = runBlocking {
        val oldLoadMore = CompletableDeferred<AppResult<Page<WrongQuestion>>>()
        val newLanguageFirstPage = CompletableDeferred<AppResult<Page<WrongQuestion>>>()
        val repository = FakePracticeRepository(
            responses = arrayDequeOf(
                immediate(page(1, 2, 2, wrong("q1"))),
                DeferredPage(oldLoadMore),
                DeferredPage(newLanguageFirstPage),
            ),
        )
        val store = FakeLearnerLanguageStore(LearnerLanguage.ALL)
        val viewModel = viewModel(repository = repository, store = store)
        viewModel.load().join()

        val loadMore = viewModel.loadMore()
        store.setLanguage(LearnerLanguage.JA)

        assertEquals(
            listOf(
                PageRequest(1, LearnerLanguage.ALL),
                PageRequest(2, LearnerLanguage.ALL),
                PageRequest(1, LearnerLanguage.JA),
            ),
            repository.requests,
        )
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(LearnerLanguage.JA, viewModel.uiState.value.language)

        oldLoadMore.complete(AppResult.Success(page(2, 2, 2, wrong("q2"))))
        newLanguageFirstPage.complete(AppResult.Success(page(1, 1, 1, wrong("jq1"))))
        loadMore?.join()
        viewModel.loadingJob?.join()

        assertEquals(listOf("jq1"), viewModel.uiState.value.items.map { it.question.id })
        assertEquals(1, viewModel.uiState.value.paged.meta.page)
        assertEquals(LearnerLanguage.JA, viewModel.uiState.value.language)
    }

    private fun viewModel(
        repository: PracticeRepository,
        store: FakeLearnerLanguageStore = FakeLearnerLanguageStore(LearnerLanguage.ALL),
    ) = WrongQuestionsViewModel(
        repository = repository,
        learnerLanguageStore = store,
        scopeOverride = CoroutineScope(Job() + Dispatchers.Unconfined),
    )

    private data class PageRequest(val page: Int, val language: LearnerLanguage)

    private sealed interface PageResponse

    private data class ImmediatePage(val value: AppResult<Page<WrongQuestion>>) : PageResponse

    private data class DeferredPage(
        val value: CompletableDeferred<AppResult<Page<WrongQuestion>>>,
    ) : PageResponse

    private class FakePracticeRepository(
        private val responses: ArrayDeque<PageResponse>,
    ) : PracticeRepository {
        val requests = mutableListOf<PageRequest>()

        override suspend fun wrongQuestions(
            page: Int,
            language: LearnerLanguage,
        ): AppResult<Page<WrongQuestion>> {
            requests += PageRequest(page, language)
            return when (val response = responses.removeFirst()) {
                is ImmediatePage -> response.value
                is DeferredPage -> response.value.await()
            }
        }

        override suspend fun summary(): AppResult<PracticeSummary> = error("unused")
        override suspend fun nextQuestion(excludeIds: List<String>): AppResult<Question> = error("unused")
        override suspend fun answerFirst(questionId: String, selectedOptionId: String): AppResult<AnswerResult> =
            error("unused")
        override suspend fun answerWrong(
            questionId: String,
            selectedOptionId: String,
            idempotencyKey: String?,
        ): AppResult<AnswerResult> =
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
