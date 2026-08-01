package com.pointquest.android.core.network

import com.pointquest.android.core.auth.RefreshCoordinator
import com.pointquest.android.core.auth.SessionManager
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStore
import com.pointquest.android.core.auth.StoredRefreshSession
import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.data.gateway.PublicAuthGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthorizedCallExecutorTest {
    @Test
    fun expiredTokenRefreshesAndReplaysOnce() = runBlocking {
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle("old-access", "old-refresh"))
        val gateway = FakePublicAuthGateway()
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)
        val executor = AuthorizedCallExecutor(state, coordinator, clock)
        var calls = 0

        val result = executor.execute<Unit> {
            calls++
            AppResult.Failure(expiredTokenError())
        }

        assertTrue(result is AppResult.Failure)
        assertEquals(2, calls)
        assertEquals(1, gateway.refreshCalls)
    }

    @Test
    fun tokenAtThirtySecondBoundaryIsRefreshedBeforeCall() = runBlocking {
        val fixture = fixture(accessExpiresAt = now.plusSeconds(30))
        var calls = 0

        val result = fixture.executor.execute {
            calls++
            AppResult.Success("ok")
        }

        assertEquals("ok", (result as AppResult.Success).value)
        assertEquals(1, calls)
        assertEquals(1, fixture.gateway.refreshCalls)
    }

    @Test
    fun tokenBeyondThirtySecondsIsUsedWithoutRefresh() = runBlocking {
        val fixture = fixture(accessExpiresAt = now.plusSeconds(31))

        val result = fixture.executor.execute { AppResult.Success("ok") }

        assertEquals("ok", (result as AppResult.Success).value)
        assertEquals(0, fixture.gateway.refreshCalls)
    }

    @Test
    fun nonExpiredToken401IsNotRefreshedOrReplayed() = runBlocking {
        val fixture = fixture()
        var calls = 0
        val denied = AppResult.Failure(
            AppError(401, "AUTH_INVALID_TOKEN", "invalid", null),
        )

        val result = fixture.executor.execute<Unit> {
            calls++
            denied
        }

        assertSame(denied, result)
        assertEquals(1, calls)
        assertEquals(0, fixture.gateway.refreshCalls)
    }

    @Test
    fun ioExceptionFromProtectedCallBecomesNetworkFailure() = runBlocking {
        val fixture = fixture()
        val failure = IOException("offline")

        val result = fixture.executor.execute<Unit> { throw failure }

        val error = (result as AppResult.Failure).error
        assertEquals("NETWORK_ERROR", error.code)
        assertSame(failure, error.cause)
    }

    @Test
    fun cancellationFromProtectedCallIsRethrown() = runBlocking {
        val fixture = fixture()
        val cancellation = CancellationException("cancelled")

        try {
            fixture.executor.execute<Unit> { throw cancellation }
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    private class FakePublicAuthGateway : PublicAuthGateway {
        var refreshCalls = 0

        override suspend fun refresh(refreshToken: String): AppResult<TokenBundle> {
            refreshCalls++
            return AppResult.Success(tokenBundle("new-access", "new-refresh"))
        }

        override suspend fun register(username: String, password: String) = error("unused")
        override suspend fun login(username: String, password: String) = error("unused")
        override suspend fun logout(refreshToken: String) = error("unused")
    }

    private class FakeSessionStore : SessionStore {
        private var value: StoredRefreshSession? = null

        override suspend fun read(): StoredRefreshSession? = value
        override suspend fun write(value: StoredRefreshSession) {
            this.value = value
        }
        override suspend fun clear() {
            value = null
        }
    }

    private companion object {
        val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

        data class Fixture(
            val executor: AuthorizedCallExecutor,
            val gateway: FakePublicAuthGateway,
        )

        suspend fun fixture(
            accessExpiresAt: Instant = now.plusSeconds(300),
        ): Fixture {
            val store = FakeSessionStore()
            val state = SessionState()
            val manager = SessionManager(store, state)
            manager.install(tokenBundle("old-access", "old-refresh", accessExpiresAt))
            val gateway = FakePublicAuthGateway()
            val coordinator = RefreshCoordinator(gateway, manager, state, clock)
            return Fixture(AuthorizedCallExecutor(state, coordinator, clock), gateway)
        }

        fun tokenBundle(
            accessToken: String,
            refreshToken: String,
            accessExpiresAt: Instant = now.plusSeconds(300),
        ) = TokenBundle(
            accessToken = accessToken,
            accessTokenExpiresAt = accessExpiresAt,
            refreshToken = refreshToken,
            refreshTokenExpiresAt = now.plusSeconds(3_600),
            user = User("student-1", "student", UserRole.STUDENT, 42),
        )

        fun expiredTokenError() = AppError(
            httpStatus = 401,
            code = "AUTH_TOKEN_EXPIRED",
            message = "expired",
            requestId = null,
        )
    }
}
