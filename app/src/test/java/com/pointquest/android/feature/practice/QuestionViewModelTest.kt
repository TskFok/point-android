package com.pointquest.android.feature.practice

import com.pointquest.android.app.PracticeMode
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.QuestionOption
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.practice.PracticeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionViewModelTest {
    @Test
    fun repeatedFirstInitializationKeepsSubmittedResultWithoutSecondRepositoryLoad() = runBlocking {
        val answer = sampleAnswer(correct = true, selectedOptionId = "o2")
        val repository = FakePracticeRepository(
            nextResults = loadResults(successQuestion("q1")),
            firstAnswerResults = arrayDequeOf(AppResult.Success(answer)),
        )
        val viewModel = firstViewModel(repository)
        viewModel.initialize()?.join()
        viewModel.selectOption("o2")
        viewModel.submit()?.join()

        val duplicate = viewModel.initialize()

        assertNull(duplicate)
        assertEquals(listOf(emptyList<String>()), repository.excludeCalls)
        assertEquals("q1", viewModel.uiState.value.question?.id)
        assertEquals(answer, viewModel.uiState.value.result)
        assertTrue(viewModel.uiState.value.submitted)
    }

    @Test
    fun repeatedWrongInitializationConsumesDraftOnlyOnceAndKeepsQuestion() = runBlocking {
        var consumeCalls = 0
        val viewModel = QuestionViewModel(
            repository = FakePracticeRepository(),
            mode = PracticeMode.WRONG,
            draftStore = PracticeDraftSource {
                consumeCalls++
                wrongQuestion("q1")
            },
            questionId = "q1",
            scopeOverride = CoroutineScope(Job() + Dispatchers.Unconfined),
        )

        viewModel.initialize()?.join()
        val duplicate = viewModel.initialize()

        assertNull(duplicate)
        assertEquals(1, consumeCalls)
        assertEquals("q1", viewModel.uiState.value.question?.id)
    }

    @Test
    fun submitWithoutSelectionDoesNotCallRepository() = runBlocking {
        val repository = FakePracticeRepository(nextResults = loadResults(successQuestion("q1")))
        val viewModel = firstViewModel(repository)
        viewModel.loadFirstQuestion().join()

        assertNull(viewModel.submit())
        assertEquals(0, repository.firstAnswerCalls.size)
        assertFalse(viewModel.uiState.value.submitted)
    }

    @Test
    fun submitLocksSelectionAndKeepsAnswerResult() = runBlocking {
        val answer = sampleAnswer(correct = false, selectedOptionId = "o2")
        val repository = FakePracticeRepository(
            nextResults = loadResults(successQuestion("q1")),
            firstAnswerResults = arrayDequeOf(AppResult.Success(answer)),
        )
        val viewModel = firstViewModel(repository)
        viewModel.loadFirstQuestion().join()
        viewModel.selectOption("o2")
        viewModel.submit()?.join()
        viewModel.selectOption("o1")

        val state = viewModel.uiState.value
        assertEquals("o2", state.selectedOptionId)
        assertTrue(state.submitted)
        assertFalse(state.selectionEnabled)
        assertEquals(answer, state.result)
    }

    @Test
    fun newestLoadWinsEvenWhenCancelledRequestReturnsLater() = runBlocking {
        val stale = CompletableDeferred<AppResult<Question>>()
        val repository = FakePracticeRepository(
            nextResults = arrayDequeOf(
                DeferredResult(stale),
                ImmediateResult(successQuestion("new")),
            ),
        )
        val viewModel = firstViewModel(repository)

        val staleJob = viewModel.loadFirstQuestion()
        viewModel.loadFirstQuestion().join()
        stale.complete(successQuestion("stale"))
        staleJob.join()

        assertEquals("new", viewModel.uiState.value.question?.id)
    }

    @Test
    fun answerFromPreviousQuestionCannotOverwriteNewlyLoadedQuestion() = runBlocking {
        val staleAnswer = CompletableDeferred<AppResult<AnswerResult>>()
        val repository = FakePracticeRepository(
            nextResults = loadResults(successQuestion("q1"), successQuestion("q2")),
            firstAnswerDeferred = staleAnswer,
        )
        val viewModel = firstViewModel(repository)
        viewModel.loadFirstQuestion().join()
        viewModel.selectOption("o1")

        val submitJob = viewModel.submit()
        viewModel.loadFirstQuestion().join()
        staleAnswer.complete(AppResult.Success(sampleAnswer(correct = true, selectedOptionId = "o1")))
        submitJob?.join()

        assertEquals("q2", viewModel.uiState.value.question?.id)
        assertFalse(viewModel.uiState.value.submitted)
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun alreadyAnsweredAutomaticallyLoadsNextQuestionWithCurrentIdExcluded() = runBlocking {
        val repository = FakePracticeRepository(
            nextResults = loadResults(successQuestion("q1"), successQuestion("q2")),
            firstAnswerResults = arrayDequeOf(failure("QUESTION_ALREADY_ANSWERED")),
        )
        val viewModel = firstViewModel(repository)
        viewModel.loadFirstQuestion().join()
        viewModel.selectOption("o1")
        viewModel.submit()?.join()
        viewModel.loadJob?.join()

        assertEquals("q2", viewModel.uiState.value.question?.id)
        assertEquals(listOf(emptyList<String>(), listOf("q1")), repository.excludeCalls)
        assertFalse(viewModel.uiState.value.submitted)
    }

    @Test
    fun noUnansweredQuestionsCompletesPractice() = runBlocking {
        val repository = FakePracticeRepository(
            nextResults = loadResults(failure("NO_UNANSWERED_QUESTIONS")),
        )
        val viewModel = firstViewModel(repository)

        viewModel.loadFirstQuestion().join()

        assertTrue(viewModel.uiState.value.completed)
        assertFalse(viewModel.uiState.value.loading)
        assertNull(viewModel.uiState.value.question)
    }

    @Test
    fun excludeIdsAreStableDistinctAndLimitedToMostRecentFifty() = runBlocking {
        val results = ArrayDeque<LoadResult>()
        (1..52).forEach { results += ImmediateResult(successQuestion("q$it")) }
        results += ImmediateResult(successQuestion("q2"))
        results += ImmediateResult(successQuestion("last"))
        val repository = FakePracticeRepository(nextResults = results)
        val viewModel = firstViewModel(repository)

        repeat(54) { viewModel.loadFirstQuestion().join() }

        assertEquals(
            (4..52).map { "q$it" } + "q2",
            repository.excludeCalls.last(),
        )
    }

    @Test
    fun wrongModeConsumesDraftAndReportsCorrectAnswerAsMastered() = runBlocking {
        val draft = wrongQuestion("q1")
        var storedDraft: WrongQuestion? = draft
        val repository = FakePracticeRepository(
            wrongAnswerResults = arrayDequeOf(AppResult.Success(sampleAnswer(true, "o2"))),
        )
        val viewModel = QuestionViewModel(
            repository = repository,
            mode = PracticeMode.WRONG,
            draftStore = PracticeDraftSource { storedDraft.also { storedDraft = null } },
            questionId = "q1",
            scopeOverride = CoroutineScope(Job() + Dispatchers.Unconfined),
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.initialize()?.join()
        viewModel.selectOption("o2")
        viewModel.submit()?.join()

        assertEquals("q1", viewModel.uiState.value.question?.id)
        assertEquals(QuestionEvent.WrongMastered("q1", returnToList = false), event.await())
    }

    @Test
    fun missingWrongQuestionDraftEmitsSingleReturnEventWithoutInventingQuestion() = runBlocking {
        val viewModel = QuestionViewModel(
            repository = FakePracticeRepository(),
            mode = PracticeMode.WRONG,
            draftStore = PracticeDraftSource { null },
            questionId = "missing",
            scopeOverride = CoroutineScope(Job() + Dispatchers.Unconfined),
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }

        viewModel.initialize()?.join()

        assertEquals(QuestionEvent.DraftMissing, event.await())
        assertNull(viewModel.uiState.value.question)
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun alreadyMasteredWrongQuestionRequestsRemovalAndReturnToList() = runBlocking {
        val repository = FakePracticeRepository(
            wrongAnswerResults = arrayDequeOf(failure("QUESTION_ALREADY_MASTERED")),
        )
        val viewModel = QuestionViewModel(
            repository = repository,
            mode = PracticeMode.WRONG,
            draftStore = PracticeDraftSource { wrongQuestion("q1") },
            questionId = "q1",
            scopeOverride = CoroutineScope(Job() + Dispatchers.Unconfined),
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.load().join()
        viewModel.selectOption("o1")

        viewModel.submit()?.join()

        assertEquals(QuestionEvent.WrongMastered("q1", returnToList = true), event.await())
        assertFalse(viewModel.uiState.value.submitting)
        assertFalse(viewModel.uiState.value.submitted)
    }

    private fun firstViewModel(repository: PracticeRepository) = QuestionViewModel(
        repository = repository,
        mode = PracticeMode.FIRST,
        draftStore = null,
        questionId = null,
        scopeOverride = CoroutineScope(Job() + Dispatchers.Unconfined),
    )

    private sealed interface LoadResult

    private data class ImmediateResult(val result: AppResult<Question>) : LoadResult

    private data class DeferredResult(
        val result: CompletableDeferred<AppResult<Question>>,
    ) : LoadResult

    private class FakePracticeRepository(
        val nextResults: ArrayDeque<LoadResult> = ArrayDeque(),
        val firstAnswerResults: ArrayDeque<AppResult<AnswerResult>> = ArrayDeque(),
        val wrongAnswerResults: ArrayDeque<AppResult<AnswerResult>> = ArrayDeque(),
        val firstAnswerDeferred: CompletableDeferred<AppResult<AnswerResult>>? = null,
    ) : PracticeRepository {
        val excludeCalls = mutableListOf<List<String>>()
        val firstAnswerCalls = mutableListOf<Pair<String, String>>()

        override suspend fun nextQuestion(excludeIds: List<String>): AppResult<Question> {
            excludeCalls += excludeIds.toList()
            return when (val result = nextResults.removeFirst()) {
                is ImmediateResult -> result.result
                is DeferredResult -> withContext(NonCancellable) { result.result.await() }
            }
        }

        override suspend fun answerFirst(questionId: String, selectedOptionId: String): AppResult<AnswerResult> {
            firstAnswerCalls += questionId to selectedOptionId
            return firstAnswerDeferred?.let { withContext(NonCancellable) { it.await() } }
                ?: firstAnswerResults.removeFirst()
        }

        override suspend fun summary(): AppResult<PracticeSummary> = error("unused")
        override suspend fun wrongQuestions(page: Int): AppResult<Page<WrongQuestion>> = error("unused")
        override suspend fun answerWrong(questionId: String, selectedOptionId: String): AppResult<AnswerResult> =
            wrongAnswerResults.removeFirst()
    }

    private companion object {
        fun successQuestion(id: String) = AppResult.Success(
            Question(
                id = id,
                stem = "1 + 1 = ?",
                basePoints = 5,
                options = listOf(
                    QuestionOption("o1", "A", "1", 1),
                    QuestionOption("o2", "B", "2", 2),
                ),
            ),
        )

        fun sampleAnswer(correct: Boolean, selectedOptionId: String) = AnswerResult(
            balance = 45,
            correct = correct,
            correctOptionId = "o2",
            errorCount = if (correct) 0 else 1,
            explanation = "因为 1 + 1 = 2",
            pointsAwarded = if (correct) 5 else 0,
            selectedOptionId = selectedOptionId,
        )

        fun failure(code: String) = AppResult.Failure(AppError(409, code, code, null))

        fun wrongQuestion(id: String) = WrongQuestion(
            errorCount = 2,
            firstAnsweredAt = Instant.parse("2030-01-01T00:00:00Z"),
            masteredAt = null,
            question = (successQuestion(id) as AppResult.Success<Question>).value,
        )

        fun loadResults(vararg values: AppResult<Question>): ArrayDeque<LoadResult> =
            ArrayDeque(values.map(::ImmediateResult))

        fun <T> arrayDequeOf(vararg values: T) = ArrayDeque(values.toList())
    }
}
