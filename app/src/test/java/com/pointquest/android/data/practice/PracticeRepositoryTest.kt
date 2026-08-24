package com.pointquest.android.data.practice

import com.pointquest.android.core.auth.RefreshCoordinator
import com.pointquest.android.core.auth.SessionManager
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStore
import com.pointquest.android.core.auth.StoredRefreshSession
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.AuthorizedCallExecutor
import com.pointquest.android.core.network.DelayProvider
import com.pointquest.android.core.network.IdempotencyKeyFactory
import com.pointquest.android.core.network.JitterSource
import com.pointquest.android.core.network.RetryExecutor
import com.pointquest.android.data.gateway.PublicAuthGateway
import com.pointquest.android.data.gateway.StudentGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeRepositoryTest {
    @Test
    fun firstAnswerUsesFrozenUuidIdempotentOperationAcrossRetry() = runBlocking {
        val gateway = FakeStudentGateway().apply {
            answerFirstResults += failure(503, "SERVICE_UNAVAILABLE")
            answerFirstResults += AppResult.Success(answer)
        }
        val repository = repository(gateway)

        val result = repository.answerFirst(questionId = "q1", selectedOptionId = "o2")

        assertTrue(result is AppResult.Success)
        assertEquals(2, gateway.firstAnswerCalls.size)
        assertEquals(1, gateway.firstAnswerCalls.map { it.key }.distinct().size)
        UUID.fromString(gateway.firstAnswerCalls.singleKey())
        assertTrue(gateway.firstAnswerCalls.all { it.questionId == "q1" && it.optionId == "o2" })
    }

    @Test
    fun firstAnswerReusesProvidedIdempotencyKeyAcrossRetry() = runBlocking {
        val gateway = FakeStudentGateway().apply {
            answerFirstResults += failure(null, "NETWORK_ERROR")
            answerFirstResults += AppResult.Success(answer)
        }
        val repository = repository(gateway)

        val result = repository.answerFirst("q1", "o1", idempotencyKey = "answer-key")

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("answer-key", "answer-key"), gateway.firstAnswerCalls.map { it.key })
    }

    @Test
    fun wrongAnswerReusesProvidedIdempotencyKeyAcrossRetry() = runBlocking {
        val gateway = FakeStudentGateway().apply {
            answerWrongResults += failure(null, "NETWORK_ERROR")
            answerWrongResults += AppResult.Success(answer)
        }

        val result = repository(gateway).answerWrong("q1", "o2", idempotencyKey = "wrong-key")

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("wrong-key", "wrong-key"), gateway.wrongAnswerCalls.map { it.key })
    }

    @Test
    fun firstAnswerPassesThroughQuestionAlreadyAnswered() = runBlocking {
        val expected = failure(409, "QUESTION_ALREADY_ANSWERED")
        val gateway = FakeStudentGateway().apply { answerFirstResults += expected }

        val result = repository(gateway).answerFirst("q1", "o2")

        assertSame(expected.error, (result as AppResult.Failure).error)
        assertEquals(1, gateway.firstAnswerCalls.size)
    }

    @Test
    fun firstAnswerUsesAuthorizedReplayWithoutChangingFrozenKey() = runBlocking {
        val authGateway = FakeAuthGateway()
        val gateway = FakeStudentGateway().apply {
            answerFirstResults += failure(401, "AUTH_TOKEN_EXPIRED")
            answerFirstResults += AppResult.Success(answer)
        }

        val result = repository(gateway, authGateway).answerFirst("q1", "o2")

        assertTrue(result is AppResult.Success)
        assertEquals(1, authGateway.refreshCalls)
        assertEquals(2, gateway.firstAnswerCalls.size)
        assertEquals(1, gateway.firstAnswerCalls.map { it.key }.distinct().size)
    }

    @Test
    fun mixedRetrySequenceSharesOneAuthRecoveryBudgetAcrossTheWholeUserOperation() = runBlocking {
        val authGateway = FakeAuthGateway()
        val gateway = FakeStudentGateway().apply {
            answerFirstResults += failure(401, "AUTH_TOKEN_EXPIRED")
            answerFirstResults += failure(503, "SERVICE_UNAVAILABLE")
            answerFirstResults += failure(401, "AUTH_TOKEN_EXPIRED")
        }

        val result = repository(gateway, authGateway).answerFirst("q1", "o2")

        assertEquals("AUTH_TOKEN_EXPIRED", (result as AppResult.Failure).error.code)
        assertEquals(1, authGateway.refreshCalls)
        assertEquals(3, gateway.firstAnswerCalls.size)
        assertEquals(1, gateway.firstAnswerCalls.map { it.key }.distinct().size)
    }

    @Test
    fun authReplayCountsTowardTheThreeBusinessSendLimit() = runBlocking {
        val authGateway = FakeAuthGateway()
        val gateway = FakeStudentGateway().apply {
            answerFirstResults += failure(503, "SERVICE_UNAVAILABLE")
            answerFirstResults += failure(401, "AUTH_TOKEN_EXPIRED")
            answerFirstResults += failure(503, "SERVICE_UNAVAILABLE")
        }

        val result = repository(gateway, authGateway).answerFirst("q1", "o2")

        assertEquals("SERVICE_UNAVAILABLE", (result as AppResult.Failure).error.code)
        assertEquals(1, authGateway.refreshCalls)
        assertEquals(3, gateway.firstAnswerCalls.size)
    }

    @Test
    fun nextQuestionTreatsInputAsOldestToNewestAndKeepsMostRecentFiftyDistinctIds() = runBlocking {
        val gateway = FakeStudentGateway()
        val ids = (1..52).map { "q$it" } + listOf("q2", "q51")

        repository(gateway).nextQuestion(ids)

        assertEquals((4..50).map { "q$it" } + listOf("q52", "q2", "q51"), gateway.randomExcludeIds)
    }

    @Test
    fun nextQuestionPassesThroughNoUnansweredQuestions() = runBlocking {
        val expected = failure(404, "NO_UNANSWERED_QUESTIONS")
        val gateway = FakeStudentGateway().apply { randomResult = expected }

        val result = repository(gateway).nextQuestion(emptyList())

        assertSame(expected.error, (result as AppResult.Failure).error)
    }

    @Test
    fun wrongQuestionsUsesRequestedPageAndFixedPageSize() = runBlocking {
        val gateway = FakeStudentGateway()

        repository(gateway).wrongQuestions(page = 3)

        assertEquals(3 to 20, gateway.wrongPage)
    }

    @Test
    fun wrongAnswerUsesFrozenUuidAcrossRetry() = runBlocking {
        val gateway = FakeStudentGateway().apply {
            answerWrongResults += failure(503, "SERVICE_UNAVAILABLE")
            answerWrongResults += AppResult.Success(answer)
        }

        val result = repository(gateway).answerWrong("q1", "o2")

        assertTrue(result is AppResult.Success)
        assertEquals(2, gateway.wrongAnswerCalls.size)
        assertEquals(1, gateway.wrongAnswerCalls.map { it.key }.distinct().size)
        UUID.fromString(gateway.wrongAnswerCalls.first().key)
        Unit
    }

    @Test
    fun wrongAnswerPassesThroughQuestionAlreadyMastered() = runBlocking {
        val expected = failure(409, "QUESTION_ALREADY_MASTERED")
        val gateway = FakeStudentGateway().apply { answerWrongResults += expected }

        val result = repository(gateway).answerWrong("q1", "o2")

        assertSame(expected.error, (result as AppResult.Failure).error)
        assertEquals(1, gateway.wrongAnswerCalls.size)
        UUID.fromString(gateway.wrongAnswerCalls.single().key)
        Unit
    }

    private suspend fun repository(
        gateway: FakeStudentGateway,
        authGateway: FakeAuthGateway = FakeAuthGateway(),
    ): DefaultPracticeRepository =
        DefaultPracticeRepository(gateway, authorizedExecutor(authGateway), retryExecutor())

    private suspend fun authorizedExecutor(authGateway: FakeAuthGateway): AuthorizedCallExecutor {
        val state = SessionState()
        val manager = SessionManager(MemoryStore(), state)
        manager.install(tokenBundle())
        return AuthorizedCallExecutor(
            state,
            RefreshCoordinator(authGateway, manager, state, clock),
            clock,
        )
    }

    private fun retryExecutor() = RetryExecutor(
        delayProvider = DelayProvider { },
        jitterSource = JitterSource { 0 },
        idempotencyKeyFactory = IdempotencyKeyFactory { UUID.randomUUID().toString() },
    )

    private class FakeStudentGateway : StudentGateway {
        data class AnswerCall(val questionId: String, val optionId: String, val key: String)

        var randomResult: AppResult<Question> = AppResult.Success(question)
        var randomExcludeIds: List<String>? = null
        var randomLanguage: LearnerLanguage? = null
        var previewRequest: Pair<Int, LearnerLanguage>? = null
        var wrongPage: Pair<Int, Int>? = null
        var wrongLanguage: LearnerLanguage? = null
        val firstAnswerCalls = mutableListOf<AnswerCall>()
        val wrongAnswerCalls = mutableListOf<AnswerCall>()
        val answerFirstResults = ArrayDeque<AppResult<AnswerResult>>()
        val answerWrongResults = ArrayDeque<AppResult<AnswerResult>>()

        override suspend fun practiceSummary(language: LearnerLanguage) = AppResult.Success(summary)
        override suspend fun randomQuestion(
            excludeIds: List<String>,
            language: LearnerLanguage,
        ): AppResult<Question> {
            randomExcludeIds = excludeIds
            randomLanguage = language
            return randomResult
        }
        override suspend fun previewQuestions(
            count: Int,
            language: LearnerLanguage,
        ): AppResult<List<Question>> {
            previewRequest = count to language
            return AppResult.Success(listOf(question))
        }
        override suspend fun answerFirst(questionId: String, optionId: String, key: String): AppResult<AnswerResult> {
            firstAnswerCalls += AnswerCall(questionId, optionId, key)
            return answerFirstResults.removeFirstOrNull() ?: AppResult.Success(answer)
        }
        override suspend fun wrongQuestions(
            page: Int,
            pageSize: Int,
            language: LearnerLanguage,
        ): AppResult<Page<WrongQuestion>> {
            wrongPage = page to pageSize
            wrongLanguage = language
            return AppResult.Success(Page(emptyList(), PageMeta(page, pageSize, 0, 0)))
        }
        override suspend fun answerWrong(questionId: String, optionId: String, key: String): AppResult<AnswerResult> {
            wrongAnswerCalls += AnswerCall(questionId, optionId, key)
            return answerWrongResults.removeFirstOrNull() ?: AppResult.Success(answer)
        }
        override suspend fun pointBalance() = error("unused")
        override suspend fun pointLedger(page: Int, pageSize: Int): AppResult<Page<PointLedgerEntry>> = error("unused")
        override suspend fun products(search: String?, page: Int, pageSize: Int): AppResult<Page<Product>> = error("unused")
        override suspend fun product(id: String): AppResult<Product> = error("unused")
        override suspend fun createOrder(productId: String, key: String): AppResult<Order> = error("unused")
        override suspend fun orders(page: Int, pageSize: Int): AppResult<Page<Order>> = error("unused")
        override suspend fun order(id: String): AppResult<Order> = error("unused")
    }

    private class MemoryStore : SessionStore {
        private var value: StoredRefreshSession? = null
        override suspend fun read() = value
        override suspend fun write(value: StoredRefreshSession) { this.value = value }
        override suspend fun clear() { value = null }
    }

    private class FakeAuthGateway : PublicAuthGateway {
        var refreshCalls = 0

        override suspend fun register(username: String, password: String) = error("unused")
        override suspend fun login(username: String, password: String) = error("unused")
        override suspend fun refresh(refreshToken: String): AppResult<TokenBundle> {
            refreshCalls++
            return AppResult.Success(tokenBundle())
        }
        override suspend fun logout(refreshToken: String) = AppResult.Success(Unit)
    }

    private companion object {
        val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
        val question = Question("q1", "1+1?", 5, emptyList())
        val answer = AnswerResult(47, true, "o2", 0, "yes", 5, "o2")
        val summary = PracticeSummary(10, 42, 2, 1, 3, 5)

        fun tokenBundle() = TokenBundle(
            "access", now.plusSeconds(300), "refresh", now.plusSeconds(3600),
            User("u1", "student", UserRole.STUDENT, 42),
        )

        fun failure(status: Int? = null, code: String) = AppResult.Failure(AppError(status, code, code, null))

        fun List<FakeStudentGateway.AnswerCall>.singleKey() = first().key
    }
}
