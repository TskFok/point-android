package com.pointquest.android.feature.home

import com.pointquest.android.R
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.auth.ActiveSession
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.points.PointsRepository
import com.pointquest.android.data.practice.PracticeRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun loadsSummaryAndBalanceConcurrentlyAndPrefersOnlineBalance() = runBlocking {
        val summaryDeferred = CompletableDeferred<AppResult<PracticeSummary>>()
        val balanceDeferred = CompletableDeferred<AppResult<Int>>()
        val practice = FakePracticeRepository(summaryDeferred)
        val points = FakePointsRepository(balanceDeferred)
        val viewModel = HomeViewModel(practice, points, signedInState(), testScope())

        assertTrue(practice.summaryStarted)
        assertTrue(points.balanceStarted)
        assertTrue(viewModel.uiState.value.loading)

        summaryDeferred.complete(AppResult.Success(summary))
        balanceDeferred.complete(AppResult.Success(99))
        viewModel.loadingJob?.join()

        val state = viewModel.uiState.value
        assertEquals(summary, state.summary)
        assertEquals(99, state.balance)
        assertEquals("student", state.username)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun partialFailureKeepsSuccessfulSummaryAndExposesRetryableError() = runBlocking {
        val practice = FakePracticeRepository(result = AppResult.Success(summary))
        val points = FakePointsRepository(result = failure("NETWORK_ERROR"))
        val viewModel = HomeViewModel(practice, points, signedInState(), testScope())
        viewModel.loadingJob?.join()

        assertEquals(summary, viewModel.uiState.value.summary)
        assertEquals(summary.balance, viewModel.uiState.value.balance)
        assertEquals(UiText.Resource(R.string.home_error_balance), viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.canRetry)

        points.result = AppResult.Success(77)
        viewModel.retry()
        viewModel.loadingJob?.join()
        assertEquals(77, viewModel.uiState.value.balance)
        assertNull(viewModel.uiState.value.error)
        assertEquals(1, practice.calls)
        assertEquals(2, points.calls)
    }

    @Test
    fun totalFailureFallsBackToLiveSessionBalanceAndCanRetryBothRequests() = runBlocking {
        val practice = FakePracticeRepository(result = failure("NETWORK_ERROR"))
        val points = FakePointsRepository(result = failure("NETWORK_ERROR"))
        val sessionState = signedInState(points = 42)
        val viewModel = HomeViewModel(practice, points, sessionState, testScope())
        viewModel.loadingJob?.join()

        assertNull(viewModel.uiState.value.summary)
        assertEquals(42, viewModel.uiState.value.balance)
        assertNotNull(viewModel.uiState.value.error)

        practice.result = AppResult.Success(summary)
        points.result = AppResult.Success(84)
        viewModel.retry()
        viewModel.loadingJob?.join()
        assertEquals(summary, viewModel.uiState.value.summary)
        assertEquals(84, viewModel.uiState.value.balance)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun completedPracticeTriggersOnlineFirstSummaryAndBalanceRefresh() = runBlocking {
        val practice = FakePracticeRepository(result = AppResult.Success(summary))
        val points = FakePointsRepository(result = AppResult.Success(50))
        val sync = AppDataSync()
        val viewModel = HomeViewModel(
            practice,
            points,
            signedInState(),
            scopeOverride = testScope(),
            appDataSync = sync,
        )
        viewModel.loadingJob?.join()
        practice.result = AppResult.Success(summary.copy(balance = 77, firstAnsweredCount = 9))
        points.result = AppResult.Success(77)

        sync.recordPracticeChanged(balance = 77)
        viewModel.loadingJob?.join()

        assertEquals(2, practice.calls)
        assertEquals(2, points.calls)
        assertEquals(9, viewModel.uiState.value.summary?.firstAnsweredCount)
        assertEquals(77, viewModel.uiState.value.balance)
    }

    private class FakePracticeRepository(
        private val deferred: CompletableDeferred<AppResult<PracticeSummary>>? = null,
        var result: AppResult<PracticeSummary> = AppResult.Success(summary),
    ) : PracticeRepository {
        var calls = 0
        var summaryStarted = false
        override suspend fun summary(): AppResult<PracticeSummary> {
            calls++
            summaryStarted = true
            return deferred?.await() ?: result
        }
        override suspend fun nextQuestion(excludeIds: List<String>): AppResult<Question> = error("unused")
        override suspend fun answerFirst(questionId: String, selectedOptionId: String): AppResult<AnswerResult> = error("unused")
        override suspend fun wrongQuestions(page: Int): AppResult<Page<WrongQuestion>> = error("unused")
        override suspend fun answerWrong(questionId: String, selectedOptionId: String): AppResult<AnswerResult> = error("unused")
    }

    private class FakePointsRepository(
        private val deferred: CompletableDeferred<AppResult<Int>>? = null,
        var result: AppResult<Int> = AppResult.Success(42),
    ) : PointsRepository {
        var calls = 0
        var balanceStarted = false
        override suspend fun balance(): AppResult<Int> {
            calls++
            balanceStarted = true
            return deferred?.await() ?: result
        }
        override suspend fun ledger(page: Int): AppResult<Page<PointLedgerEntry>> = error("unused")
    }

    private companion object {
        val summary = PracticeSummary(20, 50, 8, 3, 2, 12)
        fun failure(code: String) = AppResult.Failure(AppError(null, code, "failed", null))
        fun testScope() = CoroutineScope(Job() + Dispatchers.Unconfined)
        fun signedInState(points: Int = 42) = SessionState().apply {
            publish(
                ActiveSession(
                    user = User("student-1", "student", UserRole.STUDENT, points),
                    accessToken = "token",
                    accessTokenExpiresAt = Instant.parse("2030-01-01T00:05:00Z"),
                    generation = 1,
                ),
            )
        }
    }
}
