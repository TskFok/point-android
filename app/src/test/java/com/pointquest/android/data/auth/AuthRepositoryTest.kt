package com.pointquest.android.data.auth

import com.pointquest.android.core.auth.RefreshCoordinator
import com.pointquest.android.core.auth.SessionManager
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStore
import com.pointquest.android.core.auth.StoredRefreshSession
import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.gateway.PublicAuthGateway
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun registerReturnsUserWithoutCreatingSession() = runBlocking {
        val fixture = fixture(registerResult = AppResult.Success(student))

        val result = fixture.repository.register("student", "pass1234")

        assertEquals(student, (result as AppResult.Success).value)
        assertNull(fixture.state.active.value)
        assertNull(fixture.store.value)
    }

    @Test
    fun studentLoginInstallsSessionBeforeReturningUser() = runBlocking {
        val fixture = fixture(loginResult = AppResult.Success(studentBundle()))

        val result = fixture.repository.login("student", "pass1234")

        assertEquals(student, (result as AppResult.Success).value)
        assertEquals("access-token", fixture.state.active.value?.accessToken)
        assertEquals("refresh-token", fixture.store.value?.refreshToken)
    }

    @Test
    fun invalidCredentialsAreReturnedWithoutSession() = runBlocking {
        val denied = AppResult.Failure(
            AppError(401, "AUTH_INVALID_CREDENTIALS", "bad", "req-1"),
        )
        val fixture = fixture(loginResult = denied)

        val result = fixture.repository.login("student", "wrong")

        assertSame(denied, result)
        assertNull(fixture.state.active.value)
        assertNull(fixture.store.value)
    }

    @Test
    fun administratorLoginIsRejectedAndAnyLocalSessionIsCleared() = runBlocking {
        val fixture = fixture(loginResult = AppResult.Success(studentBundle(user = admin)))
        fixture.manager.install(studentBundle(accessToken = "previous", refreshToken = "previous-refresh"))

        val result = fixture.repository.login("admin", "pass1234")

        val error = (result as AppResult.Failure).error
        assertEquals(403, error.httpStatus)
        assertEquals("FORBIDDEN", error.code)
        assertNull(fixture.state.active.value)
        assertNull(fixture.store.value)
    }

    @Test
    fun secureStorageFailureNeverPublishesLoginSession() = runBlocking {
        val fixture = fixture(loginResult = AppResult.Success(studentBundle()))
        fixture.store.writeFailure = IOException("disk")

        val result = fixture.repository.login("student", "pass1234")

        assertEquals("SESSION_STORE_WRITE_FAILED", (result as AppResult.Failure).error.code)
        assertNull(fixture.state.active.value)
        assertNull(fixture.store.value)
    }

    @Test
    fun restoreClearsExpiredRefreshTokenWithoutNetworkCall() = runBlocking {
        val fixture = fixture()
        fixture.store.value = StoredRefreshSession("expired-refresh", now)

        val result = fixture.repository.restore()

        assertEquals("AUTH_REFRESH_EXPIRED", (result as AppResult.Failure).error.code)
        assertEquals(0, fixture.gateway.refreshCalls)
        assertNull(fixture.state.active.value)
        assertNull(fixture.store.value)
    }

    @Test
    fun restoreRotatesValidRefreshTokenAndPublishesStudent() = runBlocking {
        val fixture = fixture(refreshResult = AppResult.Success(studentBundle()))
        fixture.store.value = StoredRefreshSession("stored-refresh", now.plusSeconds(3_600))

        val result = fixture.repository.restore()

        assertEquals(student, (result as AppResult.Success).value)
        assertEquals(listOf("stored-refresh"), fixture.gateway.refreshTokens)
        assertEquals("refresh-token", fixture.store.value?.refreshToken)
        assertEquals("access-token", fixture.state.active.value?.accessToken)
        assertEquals(1L, fixture.state.active.value?.loginSessionId)
    }

    @Test
    fun restoreRejectsNonStudentBeforePublishingOrPersistingIt() = runBlocking {
        val fixture = fixture(refreshResult = AppResult.Success(studentBundle(user = admin)))
        fixture.store.value = StoredRefreshSession("stored-refresh", now.plusSeconds(3_600))

        val result = fixture.repository.restore()

        assertEquals("FORBIDDEN", (result as AppResult.Failure).error.code)
        assertNull(fixture.state.active.value)
        assertNull(fixture.store.value)
        assertTrue(fixture.store.writtenRefreshTokens.isEmpty())
    }

    @Test
    fun logoutServerFailureStillClearsLocalSession() = runBlocking {
        val serverFailure = AppResult.Failure(
            AppError(null, "NETWORK_ERROR", "offline", null),
        )
        val fixture = fixture(logoutResult = serverFailure)
        fixture.manager.install(studentBundle())

        fixture.repository.logout()

        assertEquals(listOf("refresh-token"), fixture.gateway.logoutTokens)
        assertNull(fixture.state.active.value)
        assertNull(fixture.store.value)
    }

    @Test
    fun logoutCancellationClearsLocalSessionThenRethrows() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val fixture = fixture(logoutFailure = cancellation)
        fixture.manager.install(studentBundle())

        try {
            fixture.repository.logout()
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
            assertNull(fixture.state.active.value)
            assertNull(fixture.store.value)
        }
    }

    private fun fixture(
        registerResult: AppResult<User> = AppResult.Success(student),
        loginResult: AppResult<TokenBundle> = AppResult.Success(studentBundle()),
        refreshResult: AppResult<TokenBundle> = AppResult.Success(studentBundle()),
        logoutResult: AppResult<Unit> = AppResult.Success(Unit),
        logoutFailure: Throwable? = null,
    ): Fixture {
        val store = MemoryStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        val gateway = FakeGateway(
            registerResult,
            loginResult,
            refreshResult,
            logoutResult,
            logoutFailure,
        )
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)
        return Fixture(
            repository = DefaultAuthRepository(gateway, manager, state, coordinator),
            gateway = gateway,
            manager = manager,
            state = state,
            store = store,
        )
    }

    private data class Fixture(
        val repository: AuthRepository,
        val gateway: FakeGateway,
        val manager: SessionManager,
        val state: SessionState,
        val store: MemoryStore,
    )

    private class FakeGateway(
        private val registerResult: AppResult<User>,
        private val loginResult: AppResult<TokenBundle>,
        private val refreshResult: AppResult<TokenBundle>,
        private val logoutResult: AppResult<Unit>,
        private val logoutFailure: Throwable?,
    ) : PublicAuthGateway {
        var refreshCalls = 0
        val refreshTokens = mutableListOf<String>()
        val logoutTokens = mutableListOf<String>()

        override suspend fun register(username: String, password: String) = registerResult
        override suspend fun login(username: String, password: String) = loginResult
        override suspend fun refresh(refreshToken: String): AppResult<TokenBundle> {
            refreshCalls++
            refreshTokens += refreshToken
            return refreshResult
        }
        override suspend fun logout(refreshToken: String): AppResult<Unit> {
            logoutTokens += refreshToken
            logoutFailure?.let { throw it }
            return logoutResult
        }
    }

    private class MemoryStore : SessionStore {
        var value: StoredRefreshSession? = null
        var writeFailure: Throwable? = null
        val writtenRefreshTokens = mutableListOf<String>()
        override suspend fun read() = value
        override suspend fun write(value: StoredRefreshSession) {
            writeFailure?.let { throw it }
            writtenRefreshTokens += value.refreshToken
            this.value = value
        }
        override suspend fun clear() {
            value = null
        }
    }

    private companion object {
        val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
        val student = User("student-1", "student", UserRole.STUDENT, 42)
        val admin = User("admin-1", "admin", UserRole.ADMIN, 0)

        fun studentBundle(
            accessToken: String = "access-token",
            refreshToken: String = "refresh-token",
            user: User = student,
        ) = TokenBundle(
            accessToken = accessToken,
            accessTokenExpiresAt = now.plusSeconds(300),
            refreshToken = refreshToken,
            refreshTokenExpiresAt = now.plusSeconds(3_600),
            user = user,
        )
    }
}
