package com.pointquest.android.data.points

import com.pointquest.android.core.auth.RefreshCoordinator
import com.pointquest.android.core.auth.SessionManager
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStore
import com.pointquest.android.core.auth.StoredRefreshSession
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PointLedgerType
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
import com.pointquest.android.core.network.JitterSource
import com.pointquest.android.core.network.RetryExecutor
import com.pointquest.android.data.gateway.PublicAuthGateway
import com.pointquest.android.data.gateway.StudentGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PointsRepositoryTest {
    @Test
    fun balanceUsesAuthorizedReadRetryWithoutNestedRetries() = runBlocking {
        val gateway = FakeStudentGateway().apply {
            balances += failure(503, "SERVICE_UNAVAILABLE")
            balances += AppResult.Success(42)
        }

        val result = repository(gateway).balance()

        assertEquals(42, (result as AppResult.Success).value)
        assertEquals(2, gateway.balanceCalls)
    }

    @Test
    fun ledgerUsesRequestedPageAndFixedPageSize() = runBlocking {
        val gateway = FakeStudentGateway()

        val result = repository(gateway).ledger(4)

        assertEquals(4 to 20, gateway.ledgerPage)
        assertEquals(ledger, (result as AppResult.Success).value.items.single())
    }

    @Test
    fun ledgerPassesThroughServerErrorCodeUnchanged() = runBlocking {
        val expected = failure(400, "INVALID_PAGE")
        val gateway = FakeStudentGateway().apply { ledgerResult = expected }

        val result = repository(gateway).ledger(0)

        assertSame(expected.error, (result as AppResult.Failure).error)
    }

    private suspend fun repository(gateway: StudentGateway): DefaultPointsRepository {
        val state = SessionState()
        val manager = SessionManager(MemoryStore(), state)
        manager.install(tokenBundle())
        val authorized = AuthorizedCallExecutor(
            state,
            RefreshCoordinator(FakeAuthGateway(), manager, state, clock),
            clock,
        )
        val retry = RetryExecutor(
            delayProvider = DelayProvider { },
            jitterSource = JitterSource { 0 },
        )
        return DefaultPointsRepository(gateway, authorized, retry)
    }

    private class FakeStudentGateway : StudentGateway {
        val balances = ArrayDeque<AppResult<Int>>()
        var balanceCalls = 0
        var ledgerPage: Pair<Int, Int>? = null
        var ledgerResult: AppResult<Page<PointLedgerEntry>> =
            AppResult.Success(Page(listOf(ledger), PageMeta(1, 20, 1, 1)))

        override suspend fun pointBalance(): AppResult<Int> {
            balanceCalls++
            return balances.removeFirstOrNull() ?: AppResult.Success(42)
        }
        override suspend fun pointLedger(page: Int, pageSize: Int): AppResult<Page<PointLedgerEntry>> {
            ledgerPage = page to pageSize
            return ledgerResult
        }
        override suspend fun practiceSummary(): AppResult<PracticeSummary> = error("unused")
        override suspend fun randomQuestion(excludeIds: List<String>): AppResult<Question> = error("unused")
        override suspend fun answerFirst(questionId: String, optionId: String, key: String): AppResult<AnswerResult> = error("unused")
        override suspend fun wrongQuestions(page: Int, pageSize: Int): AppResult<Page<WrongQuestion>> = error("unused")
        override suspend fun answerWrong(questionId: String, optionId: String, key: String): AppResult<AnswerResult> = error("unused")
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
        override suspend fun register(username: String, password: String) = error("unused")
        override suspend fun login(username: String, password: String) = error("unused")
        override suspend fun refresh(refreshToken: String) = AppResult.Success(tokenBundle())
        override suspend fun logout(refreshToken: String) = AppResult.Success(Unit)
    }

    private companion object {
        val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
        val ledger = PointLedgerEntry(
            "l1", "u1", PointLedgerType.ANSWER_REWARD, 5, 42, "a1", null, now,
        )
        fun tokenBundle() = TokenBundle(
            "access", now.plusSeconds(300), "refresh", now.plusSeconds(3600),
            User("u1", "student", UserRole.STUDENT, 42),
        )
        fun failure(status: Int, code: String) = AppResult.Failure(AppError(status, code, code, null))
    }
}
