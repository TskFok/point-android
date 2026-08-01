package com.pointquest.android.core.auth

import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.gateway.PublicAuthGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
        val store = FakeSessionStore()
        val state = SessionState()
        val manager = SessionManager(store, state)
        manager.install(tokenBundle(accessToken = "old-access", refreshToken = "old-refresh"))
        val gateway = FakePublicAuthGateway(refreshDelayMs = 50)
        val coordinator = RefreshCoordinator(gateway, manager, state, clock)

        coroutineScope {
            repeat(20) {
                launch {
                    val result = coordinator.refresh(force = true, observedGeneration = 1L)
                    assertTrue(result is AppResult.Success)
                }
            }
        }

        assertEquals(1, gateway.refreshCalls)
        assertEquals(listOf("old-refresh"), gateway.refreshTokens)
        assertEquals("rotated-refresh", store.value?.refreshToken)
        assertEquals(2L, state.active.value?.generation)
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
        private val refreshDelayMs: Long = 0,
        private val refreshResult: AppResult<TokenBundle>? = null,
        private val refreshFailure: Throwable? = null,
    ) : PublicAuthGateway {
        var refreshCalls = 0
        val refreshTokens = mutableListOf<String>()

        override suspend fun refresh(refreshToken: String): AppResult<TokenBundle> {
            refreshCalls++
            refreshTokens += refreshToken
            delay(refreshDelayMs)
            refreshFailure?.let { throw it }
            refreshResult?.let { return it }
            return AppResult.Success(
                tokenBundle(accessToken = "rotated-access", refreshToken = "rotated-refresh"),
            )
        }

        override suspend fun register(username: String, password: String) = error("unused")
        override suspend fun login(username: String, password: String) = error("unused")
        override suspend fun logout(refreshToken: String) = error("unused")
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
        ) = TokenBundle(
            accessToken = accessToken,
            accessTokenExpiresAt = now.plusSeconds(300),
            refreshToken = refreshToken,
            refreshTokenExpiresAt = refreshExpiresAt,
            user = User("student-1", "student", UserRole.STUDENT, 42),
        )
    }
}
