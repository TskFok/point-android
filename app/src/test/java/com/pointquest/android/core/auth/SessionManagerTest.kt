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
    fun refreshLeaseAtomicallyCapturesStoredSessionAndEpoch() = runBlocking {
        val expected = StoredRefreshSession(
            refreshToken = "refresh-token",
            expiresAt = Instant.parse("2030-02-01T00:00:00Z"),
        )
        val store = FakeSessionStore().apply { lastWritten = expected }
        val manager = SessionManager(store, SessionState())

        val result = manager.acquireRefreshLease()

        val lease = (result as AppResult.Success).value
        assertEquals(expected, lease?.storedSession)
        assertEquals(0L, lease?.epoch)
    }

    @Test
    fun clearAdvancesEpochEvenWhenSessionIsAlreadyEmpty() = runBlocking {
        val manager = SessionManager(FakeSessionStore(), SessionState())

        manager.clear()
        manager.clear()
        val installed = manager.install(sampleTokenBundle())

        assertEquals(3L, (installed as AppResult.Success).value.generation)
    }

    @Test
    fun cancellationWhileReadingRefreshLeaseClearsSessionAndRethrowsOriginal() = runBlocking {
        val cancellation = CancellationException("read cancelled")
        val state = SessionState()
        val store = FakeSessionStore()
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle())
        store.readError = cancellation

        try {
            manager.acquireRefreshLease()
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
            assertNull(state.active.value)
            assertNull(store.lastWritten)
        }
    }

    @Test
    fun readFailureKeepsOriginalErrorWhenCleanupIsCancelled() = runBlocking {
        val readFailure = IOException("read failed")
        val cleanupCancellation = CancellationException("cleanup cancelled")
        val state = SessionState()
        val store = FakeSessionStore()
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle())
        store.readError = readFailure
        store.clearError = cleanupCancellation

        val result = manager.acquireRefreshLease()

        val error = (result as AppResult.Failure).error
        assertEquals("SESSION_STORE_READ_FAILED", error.code)
        assertSame(readFailure, error.cause)
        assertNull(state.active.value)
        assertTrue(store.clearCalled)

        store.readError = null
        store.clearError = null
        val nextLease = manager.acquireRefreshLease()
        assertNull((nextLease as AppResult.Success).value)
    }

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
        assertEquals(3L, (recovered as AppResult.Success).value.generation)
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
    fun cancellationDuringInstallClearsPublishedAndPersistedSessionsBeforeRethrow() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val state = SessionState()
        val store = FakeSessionStore(onClear = { assertNull(state.active.value) })
        val manager = SessionManager(store, state)
        manager.install(
            sampleTokenBundle(accessToken = "old-access-token", refreshToken = "old-refresh-token"),
        )
        store.writeBeforeError = true
        store.writeError = cancellation

        try {
            manager.install(
                sampleTokenBundle(accessToken = "new-access-token", refreshToken = "new-refresh-token"),
            )
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
            assertNull(state.active.value)
            assertTrue(store.clearCalled)
            assertNull(store.lastWritten)
        }
    }

    @Test
    fun cancellationWhileWaitingToInstallClearsAnySessionPublishedAheadOfIt() = runBlocking {
        val blockingWriteEntered = CompletableDeferred<Unit>()
        val releaseBlockingWrite = CompletableDeferred<Unit>()
        val state = SessionState()
        val store = FakeSessionStore(
            onWrite = { value ->
                if (value.refreshToken == "blocking-refresh-token") {
                    blockingWriteEntered.complete(Unit)
                    releaseBlockingWrite.await()
                }
            },
        )
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle(accessToken = "old-access-token"))
        val blockingInstall = async {
            manager.install(
                sampleTokenBundle(
                    accessToken = "blocking-access-token",
                    refreshToken = "blocking-refresh-token",
                ),
            )
        }
        blockingWriteEntered.await()
        val waitingInstall = async {
            manager.install(sampleTokenBundle(accessToken = "waiting-access-token"))
        }
        yield()

        waitingInstall.cancel(CancellationException("cancelled while waiting"))
        releaseBlockingWrite.complete(Unit)
        blockingInstall.await()
        try {
            waitingInstall.await()
            fail("CancellationException should be rethrown")
        } catch (_: CancellationException) {
            assertNull(state.active.value)
            assertTrue(store.clearCalled)
            assertNull(store.lastWritten)
        }
    }

    @Test
    fun cancellationCleanupFailureNeverReplacesOriginalCancellation() = runBlocking {
        val cancellation = CancellationException("original cancellation")
        val state = SessionState()
        val store = FakeSessionStore(clearError = IOException("cleanup failed"))
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle(accessToken = "old-access-token"))
        store.writeError = cancellation

        try {
            manager.install(sampleTokenBundle(accessToken = "new-access-token"))
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
            assertNull(state.active.value)
            assertTrue(store.clearCalled)
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
        var clearError: Throwable? = null,
        private val onWrite: (suspend (StoredRefreshSession) -> Unit)? = null,
        private val onClear: (suspend () -> Unit)? = null,
    ) : SessionStore {
        var clearCalled = false
        var lastWritten: StoredRefreshSession? = null
        var writeBeforeError = false
        var readError: Throwable? = null

        override suspend fun read(): StoredRefreshSession? {
            readError?.let { throw it }
            return lastWritten
        }

        override suspend fun write(value: StoredRefreshSession) {
            onWrite?.invoke(value)
            if (writeBeforeError) lastWritten = value
            writeError?.let { throw it }
            lastWritten = value
        }

        override suspend fun clear() {
            clearCalled = true
            onClear?.invoke()
            clearError?.let { throw it }
            lastWritten = null
        }
    }
}
