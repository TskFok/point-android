package com.pointquest.android.core.auth

import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppResult
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionManagerTest {
    @Test
    fun storeFailureNeverPublishesAccessToken() = runBlocking {
        val store = FakeSessionStore(writeError = IOException("disk"))
        val state = SessionState()
        val manager = SessionManager(store, state)

        val result = manager.install(sampleTokenBundle())

        assertTrue(result is AppResult.Failure)
        assertNull(state.active.value)
        assertTrue(store.clearCalled)
    }

    @Test
    fun accessTokenIsPublishedOnlyAfterRefreshSessionIsStored() = runBlocking {
        val state = SessionState()
        val store = FakeSessionStore(
            onWrite = { assertNull(state.active.value) },
        )
        val manager = SessionManager(store, state)

        val result = manager.install(sampleTokenBundle())

        assertTrue(result is AppResult.Success)
        assertEquals("access-token", state.active.value?.accessToken)
        assertEquals("refresh-token", store.lastWritten?.refreshToken)
    }

    @Test
    fun repeatedInstallsIncreaseGenerationInSuccessOrder() = runBlocking {
        val state = SessionState()
        val store = FakeSessionStore()
        val manager = SessionManager(store, state)

        val first = manager.install(sampleTokenBundle(accessToken = "access-1"))
        val second = manager.install(sampleTokenBundle(accessToken = "access-2"))

        assertEquals(1L, (first as AppResult.Success).value.generation)
        assertEquals(2L, (second as AppResult.Success).value.generation)
        assertEquals("access-2", state.active.value?.accessToken)
        assertEquals(2L, state.active.value?.generation)
    }

    @Test
    fun concurrentInstallsPublishInSerializedWriteOrder() = runBlocking {
        val firstWriteEntered = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val secondWriteEntered = CompletableDeferred<Unit>()
        val store = FakeSessionStore(
            onWrite = { value ->
                if (value.refreshToken == "refresh-1") {
                    firstWriteEntered.complete(Unit)
                    releaseFirstWrite.await()
                } else {
                    secondWriteEntered.complete(Unit)
                }
            },
        )
        val state = SessionState()
        val manager = SessionManager(store, state)

        val first = async {
            manager.install(sampleTokenBundle(accessToken = "access-1", refreshToken = "refresh-1"))
        }
        firstWriteEntered.await()
        val second = async {
            manager.install(sampleTokenBundle(accessToken = "access-2", refreshToken = "refresh-2"))
        }
        yield()

        assertFalse(secondWriteEntered.isCompleted)
        releaseFirstWrite.complete(Unit)
        assertEquals(1L, (first.await() as AppResult.Success).value.generation)
        assertEquals(2L, (second.await() as AppResult.Success).value.generation)
        assertEquals("access-2", state.active.value?.accessToken)
    }

    @Test
    fun failedReplacementClearsPreviouslyPublishedSession() = runBlocking {
        val state = SessionState()
        val store = FakeSessionStore()
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle(accessToken = "old-access"))
        store.writeError = IOException("disk")

        val result = manager.install(sampleTokenBundle(accessToken = "new-access"))

        val error = (result as AppResult.Failure).error
        assertEquals("SESSION_STORE_WRITE_FAILED", error.code)
        assertNull(state.active.value)
        assertTrue(store.clearCalled)

        store.writeError = null
        val recovered = manager.install(sampleTokenBundle(accessToken = "recovered-access"))
        assertEquals(2L, (recovered as AppResult.Success).value.generation)
    }

    @Test
    fun cleanupFailureKeepsOriginalInstallErrorStable() = runBlocking {
        val writeError = IOException("write")
        val store = FakeSessionStore(
            writeError = writeError,
            clearError = IOException("clear"),
        )
        val manager = SessionManager(store, SessionState())

        val result = manager.install(sampleTokenBundle())

        val error = (result as AppResult.Failure).error
        assertEquals("SESSION_STORE_WRITE_FAILED", error.code)
        assertSame(writeError, error.cause)
    }

    @Test
    fun clearFailureStillClearsMemoryAndReturnsFailure() = runBlocking {
        val state = SessionState()
        val store = FakeSessionStore(clearError = IOException("disk"))
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle())

        val result = manager.clear()

        val error = (result as AppResult.Failure).error
        assertEquals("SESSION_STORE_CLEAR_FAILED", error.code)
        assertNull(state.active.value)
    }

    @Test
    fun successfulClearClearsMemoryAndStore() = runBlocking {
        val state = SessionState()
        val store = FakeSessionStore()
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle())

        val result = manager.clear()

        assertTrue(result is AppResult.Success)
        assertNull(state.active.value)
        assertTrue(store.clearCalled)
    }

    @Test
    fun cancellationDuringInstallIsRethrown() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val manager = SessionManager(
            FakeSessionStore(writeError = cancellation),
            SessionState(),
        )

        try {
            manager.install(sampleTokenBundle())
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun cancellationDuringClearIsRethrownAfterMemoryIsCleared() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val state = SessionState()
        val store = FakeSessionStore(clearError = cancellation)
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle())

        try {
            manager.clear()
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
            assertNull(state.active.value)
        }
    }

    private fun sampleTokenBundle(
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
    ) = TokenBundle(
        accessToken = accessToken,
        accessTokenExpiresAt = Instant.parse("2030-01-01T01:00:00Z"),
        refreshToken = refreshToken,
        refreshTokenExpiresAt = Instant.parse("2030-02-01T00:00:00Z"),
        user = User(
            id = "student-1",
            username = "student",
            role = UserRole.STUDENT,
            pointsBalance = 42,
        ),
    )

    private class FakeSessionStore(
        var writeError: Throwable? = null,
        private val clearError: Throwable? = null,
        private val onWrite: (suspend (StoredRefreshSession) -> Unit)? = null,
    ) : SessionStore {
        var clearCalled = false
        var lastWritten: StoredRefreshSession? = null

        override suspend fun read(): StoredRefreshSession? = null

        override suspend fun write(value: StoredRefreshSession) {
            onWrite?.invoke(value)
            writeError?.let { throw it }
            lastWritten = value
        }

        override suspend fun clear() {
            clearCalled = true
            clearError?.let { throw it }
            lastWritten = null
        }
    }
}
