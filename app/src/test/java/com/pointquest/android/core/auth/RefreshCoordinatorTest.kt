package com.pointquest.android.core.auth

import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.auth.DefaultAuthRepository
import com.pointquest.android.data.gateway.PublicAuthGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RefreshCoordinatorTest {
    @Test
    fun concurrentRefreshesUseOneNetworkCall() = runBlocking {
        val readyCount = java.util.concurrent.atomic.AtomicInteger()
        val allReady = CompletableDeferred<Unit>()
        val start = CompletableDeferred<Unit>()
        val refreshEntered = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        val installed = manager.install(
            tokenBundle(accessToken = "old-access", refreshToken = "old-refresh"),
        ) as AppResult.Success
        val gateway = FakePublicAuthGateway(
            refreshEntered = refreshEntered,
            releaseRefresh = releaseRefresh,
        )
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)

        coroutineScope {
            val jobs = List(20) {
                launch {
                    if (readyCount.incrementAndGet() == 20) allReady.complete(Unit)
                    start.await()
                    val result = coordinator.refresh(force = true, observedGeneration = 1L)
                    assertTrue(result is AppResult.Success)
                }
            }
            allReady.await()
            start.complete(Unit)
            refreshEntered.await()
            assertEquals(1, gateway.refreshCalls)
            releaseRefresh.complete(Unit)
            jobs.joinAll()
        }

        assertEquals(1, gateway.refreshCalls)
        assertEquals(listOf("old-refresh"), gateway.refreshTokens)
        assertEquals("rotated-refresh", store.value?.refreshToken)
        assertEquals(2L, state.active.value?.generation)
        assertEquals(installed.value.loginSessionId, state.active.value?.loginSessionId)
    }

    @Test
    fun staleGenerationReturnsCurrentSessionWithoutReusingRotatedToken() = runBlocking {
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle("access-1", "refresh-1"))
        manager.install(tokenBundle("access-2", "refresh-2"))
        val gateway = FakePublicAuthGateway()
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)

        val result = coordinator.refresh(force = true, observedGeneration = 1L)

        assertEquals("access-2", (result as AppResult.Success).value.accessToken)
        assertEquals(0, gateway.refreshCalls)
        assertEquals("refresh-2", store.value?.refreshToken)
    }

    @Test
    fun refreshResponseAfterLogoutCannotResurrectSession() = runBlocking {
        val refreshEntered = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle("old-access", "old-refresh"))
        val gateway = FakePublicAuthGateway(
            refreshEntered = refreshEntered,
            releaseRefresh = releaseRefresh,
        )
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)
        val repository = DefaultAuthRepository(gateway, manager, state, coordinator)

        val refreshing = async {
            coordinator.refresh(force = true, observedGeneration = 1L)
        }
        refreshEntered.await()
        repository.logout()
        releaseRefresh.complete(Unit)

        val result = refreshing.await()
        assertEquals("AUTH_SESSION_CHANGED", (result as AppResult.Failure).error.code)
        assertNull(state.active.value)
        assertNull(store.value)
    }

    @Test
    fun refreshResponseFromOldAccountCannotOverwriteConcurrentLogin() = runBlocking {
        val refreshEntered = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle("old-access", "old-refresh"))
        val newUser = User("student-2", "new-student", UserRole.STUDENT, 7)
        val newBundle = tokenBundle("new-access", "new-refresh", user = newUser)
        val gateway = FakePublicAuthGateway(
            refreshEntered = refreshEntered,
            releaseRefresh = releaseRefresh,
            loginResult = AppResult.Success(newBundle),
        )
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)
        val repository = DefaultAuthRepository(gateway, manager, state, coordinator)

        val refreshing = async {
            coordinator.refresh(force = true, observedGeneration = 1L)
        }
        refreshEntered.await()
        val login = repository.login("new-student", "pass1234")
        releaseRefresh.complete(Unit)

        assertEquals(newUser, (login as AppResult.Success).value)
        assertEquals("AUTH_SESSION_CHANGED", (refreshing.await() as AppResult.Failure).error.code)
        assertEquals("new-access", state.active.value?.accessToken)
        assertEquals(newUser, state.active.value?.user)
        assertEquals("new-refresh", store.value?.refreshToken)
    }

    @Test
    fun expiredStoredRefreshTokenIsClearedWithoutNetworkCall() = runBlocking {
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(
            tokenBundle("old-access", "expired-refresh", refreshExpiresAt = now),
        )
        val gateway = FakePublicAuthGateway()
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)

        val result = coordinator.refresh(force = true, observedGeneration = 1L)

        assertEquals("AUTH_REFRESH_EXPIRED", (result as AppResult.Failure).error.code)
        assertEquals(0, gateway.refreshCalls)
        assertNull(state.active.value)
        assertNull(store.value)
    }

    @Test
    fun missingStoredRefreshSessionClearsActiveAccessSession() = runBlocking {
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle("old-access", "old-refresh"))
        store.value = null
        val gateway = FakePublicAuthGateway()
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)

        val result = coordinator.refresh(force = true, observedGeneration = 1L)

        assertEquals("AUTH_SESSION_MISSING", (result as AppResult.Failure).error.code)
        assertEquals(0, gateway.refreshCalls)
        assertNull(state.active.value)
    }

    @Test
    fun networkFailureClearsSessionAndNeverRetriesRefreshToken() = runBlocking {
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle("old-access", "old-refresh"))
        val failure = AppResult.Failure(
            com.pointquest.android.core.network.AppError(
                httpStatus = null,
                code = "NETWORK_ERROR",
                message = "network",
                requestId = null,
            ),
        )
        val gateway = FakePublicAuthGateway(refreshResult = failure)
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)

        val result = coordinator.refresh(force = true, observedGeneration = 1L)

        assertSame(failure, result)
        assertEquals(listOf("old-refresh"), gateway.refreshTokens)
        assertNull(state.active.value)
        assertNull(store.value)
    }

    @Test
    fun thrownIoDuringRefreshBecomesNetworkFailureAndClearsSession() = runBlocking {
        val io = java.io.IOException("offline")
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle("old-access", "old-refresh"))
        val gateway = FakePublicAuthGateway(refreshFailure = io)
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)

        val result = coordinator.refresh(force = true, observedGeneration = 1L)

        val error = (result as AppResult.Failure).error
        assertEquals("NETWORK_ERROR", error.code)
        assertSame(io, error.cause)
        assertNull(state.active.value)
        assertNull(store.value)
    }

    @Test
    fun semanticMappingFailureClearsSessionAndNeverReusesOldToken() = runBlocking {
        val mappingFailure = com.squareup.moshi.JsonDataException("missing accessToken")
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle("old-access", "old-refresh"))
        val gateway = FakePublicAuthGateway(refreshFailure = mappingFailure)
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)

        val first = coordinator.refresh(force = true, observedGeneration = 1L)
        val second = coordinator.refresh(force = true, observedGeneration = 1L)

        assertEquals("INVALID_RESPONSE", (first as AppResult.Failure).error.code)
        assertEquals("AUTH_SESSION_MISSING", (second as AppResult.Failure).error.code)
        assertEquals(listOf("old-refresh"), gateway.refreshTokens)
        assertNull(state.active.value)
        assertNull(store.value)
    }

    @Test
    fun secureWriteFailureAfterRotationLeavesNoSession() = runBlocking {
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle("old-access", "old-refresh"))
        store.writeFailure = java.io.IOException("disk")
        val gateway = FakePublicAuthGateway()
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)

        val result = coordinator.refresh(force = true, observedGeneration = 1L)

        assertEquals("SESSION_STORE_WRITE_FAILED", (result as AppResult.Failure).error.code)
        assertEquals(listOf("old-refresh"), gateway.refreshTokens)
        assertNull(state.active.value)
        assertNull(store.value)
    }

    @Test
    fun cancellationDuringUncertainRefreshClearsSessionThenRethrows() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle("old-access", "old-refresh"))
        val gateway = FakePublicAuthGateway(refreshFailure = cancellation)
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)

        try {
            coordinator.refresh(force = true, observedGeneration = 1L)
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
            assertNull(state.active.value)
            assertNull(store.value)
        }
    }

    private class FakePublicAuthGateway(
        private val refreshResult: AppResult<TokenBundle>? = null,
        private val refreshFailure: Throwable? = null,
        private val refreshEntered: CompletableDeferred<Unit>? = null,
        private val releaseRefresh: CompletableDeferred<Unit>? = null,
        private val loginResult: AppResult<TokenBundle>? = null,
    ) : PublicAuthGateway {
        var refreshCalls = 0
        val refreshTokens = mutableListOf<String>()

        override suspend fun refresh(refreshToken: String): AppResult<TokenBundle> {
            refreshCalls++
            refreshTokens += refreshToken
            refreshEntered?.complete(Unit)
            releaseRefresh?.await()
            refreshFailure?.let { throw it }
            refreshResult?.let { return it }
            return AppResult.Success(
                tokenBundle(accessToken = "rotated-access", refreshToken = "rotated-refresh"),
            )
        }

        override suspend fun register(username: String, password: String) = error("unused")
        override suspend fun login(username: String, password: String): AppResult<TokenBundle> =
            loginResult ?: error("unused")
        override suspend fun logout(refreshToken: String) = AppResult.Success(Unit)
    }

    private class FakeSessionStore : SessionStore {
        var value: StoredRefreshSession? = null
        var writeFailure: Throwable? = null

        override suspend fun read(): StoredRefreshSession? = value
        override suspend fun write(value: StoredRefreshSession) {
            writeFailure?.let { throw it }
            this.value = value
        }
        override suspend fun clear() {
            value = null
        }
    }

    private companion object {
        val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

        fun tokenBundle(
            accessToken: String,
            refreshToken: String,
            refreshExpiresAt: Instant = now.plusSeconds(3_600),
            user: User = User("student-1", "student", UserRole.STUDENT, 42),
        ) = TokenBundle(
            accessToken = accessToken,
            accessTokenExpiresAt = now.plusSeconds(300),
            refreshToken = refreshToken,
            refreshTokenExpiresAt = refreshExpiresAt,
            user = user,
        )
    }
}
