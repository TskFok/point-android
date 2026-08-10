package com.pointquest.android.core.auth

import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppResult
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
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

        val snapshot = (result as AppResult.Success).value
        val available = snapshot as RefreshMaterialSnapshot.Available
        assertEquals(expected, available.storedSession)
        assertEquals(0L, available.epoch)
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
        val nextSnapshot = manager.acquireRefreshLease()
        assertTrue((nextSnapshot as AppResult.Success).value is RefreshMaterialSnapshot.Missing)
    }

    @Test
    fun missingSnapshotCannotClearLoginThatAdvancedEpoch() = runBlocking {
        val snapshotTaken = CompletableDeferred<Unit>()
        val continueClear = CompletableDeferred<Unit>()
        val state = SessionState()
        val store = FakeSessionStore()
        val manager = SessionManager(store, state)

        val oldMissingClear = async {
            val snapshot = (manager.acquireRefreshLease() as AppResult.Success).value
            val missing = snapshot as RefreshMaterialSnapshot.Missing
            snapshotTaken.complete(Unit)
            continueClear.await()
            manager.clearIfEpochMatches(missing.epoch)
        }
        snapshotTaken.await()
        val newUser = User("student-2", "new-student", UserRole.STUDENT, 7)
        manager.install(
            sampleTokenBundle(
                accessToken = "new-access",
                refreshToken = "new-refresh",
                user = newUser,
            ),
        )
        continueClear.complete(Unit)

        assertFalse(oldMissingClear.await())
        assertEquals(newUser, state.active.value?.user)
        assertEquals("new-access", state.active.value?.accessToken)
        assertEquals("new-refresh", store.lastWritten?.refreshToken)
    }

    @Test
    fun cancellingStaleCommitWaitingForLoginMutexDoesNotClearNewLogin() = runBlocking {
        val newWriteEntered = CompletableDeferred<Unit>()
        val releaseNewWrite = CompletableDeferred<Unit>()
        val state = SessionState()
        val store = FakeSessionStore(
            onWrite = { stored ->
                if (stored.refreshToken == "new-refresh") {
                    newWriteEntered.complete(Unit)
                    releaseNewWrite.await()
                }
            },
        )
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle(accessToken = "old-access", refreshToken = "old-refresh"))
        val snapshot = (manager.acquireRefreshLease() as AppResult.Success).value
        val oldLease = snapshot as RefreshMaterialSnapshot.Available
        val newUser = User("student-2", "new-student", UserRole.STUDENT, 7)
        val newLogin = async {
            manager.install(
                sampleTokenBundle(
                    accessToken = "new-access",
                    refreshToken = "new-refresh",
                    user = newUser,
                ),
            )
        }
        newWriteEntered.await()

        val oldCommit = async(start = CoroutineStart.UNDISPATCHED) {
            manager.commitRefresh(
                oldLease,
                sampleTokenBundle(
                    accessToken = "old-rotated-access",
                    refreshToken = "old-rotated-refresh",
                ),
            )
        }
        val cancellation = CancellationException("cancel stale commit waiting for mutex")
        oldCommit.cancel(cancellation)
        releaseNewWrite.complete(Unit)
        val installed = newLogin.await()

        try {
            oldCommit.await()
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertEquals(cancellation.message, actual.message)
        }
        assertEquals(newUser, (installed as AppResult.Success).value.user)
        assertEquals(newUser, state.active.value?.user)
        assertEquals("new-access", state.active.value?.accessToken)
        assertEquals("new-refresh", store.lastWritten?.refreshToken)
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

        val firstSession = (first as AppResult.Success).value
        val secondSession = (second as AppResult.Success).value
        assertEquals(1L, firstSession.generation)
        assertEquals(2L, secondSession.generation)
        assertTrue(firstSession.loginSessionId != secondSession.loginSessionId)
        assertEquals("access-2", state.active.value?.accessToken)
        assertEquals(2L, state.active.value?.generation)
    }

    @Test
    fun credentialRefreshAdvancesGenerationButPreservesLoginSessionIdentity() = runBlocking {
        val state = SessionState()
        val manager = SessionManager(FakeSessionStore(), state)
        val installed = manager.install(
            sampleTokenBundle(accessToken = "old-access", refreshToken = "old-refresh"),
        ) as AppResult.Success
        val lease = (manager.acquireRefreshLease() as AppResult.Success).value as
            RefreshMaterialSnapshot.Available

        val committed = manager.commitRefresh(
            lease,
            sampleTokenBundle(accessToken = "new-access", refreshToken = "new-refresh"),
        ) as AppResult.Success

        val refreshed = (committed.value as RefreshCommit.Installed).session
        assertEquals(installed.value.loginSessionId, refreshed.loginSessionId)
        assertEquals(installed.value.generation + 1, refreshed.generation)
        assertEquals("new-access", state.active.value?.accessToken)
    }

    @Test
    fun credentialRefreshCannotInstallAResponseForAnotherUser() = runBlocking {
        val state = SessionState()
        val store = FakeSessionStore()
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle(accessToken = "old-access", refreshToken = "old-refresh"))
        val lease = (manager.acquireRefreshLease() as AppResult.Success).value as
            RefreshMaterialSnapshot.Available
        val otherUser = User("student-2", "other", UserRole.STUDENT, 7)

        val committed = manager.commitRefresh(
            lease,
            sampleTokenBundle(
                accessToken = "wrong-user-access",
                refreshToken = "wrong-user-refresh",
                user = otherUser,
            ),
        ) as AppResult.Success

        assertTrue(committed.value is RefreshCommit.Stale)
        assertNull(state.active.value)
        assertNull(store.lastWritten)
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
    fun observerFailureDuringClearMustStillErasePersistedRefreshSession() = runBlocking {
        val observerFailure = IllegalStateException("observer failed")
        val state = SessionState()
        val store = FakeSessionStore()
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle())
        state.observeActiveSession { session ->
            if (session == null) throw observerFailure
        }

        try {
            manager.clear()
            fail("observer failure should be rethrown after persistent cleanup")
        } catch (actual: IllegalStateException) {
            assertSame(observerFailure, actual)
        }

        assertNull(state.active.value)
        assertEquals(SessionStatus.SignedOut, state.status.value)
        assertTrue(store.clearCalled)
        assertNull(store.lastWritten)
    }

    @Test
    fun observerFailureDuringClearSuppressesStoreFailureAfterAttemptingBothLayers() = runBlocking {
        val observerFailure = IllegalStateException("observer failed")
        val storeFailure = IOException("store clear failed")
        val state = SessionState()
        val store = FakeSessionStore(clearError = storeFailure)
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle())
        state.observeActiveSession { session ->
            if (session == null) throw observerFailure
        }

        try {
            manager.clear()
            fail("observer failure should remain primary")
        } catch (actual: IllegalStateException) {
            assertSame(observerFailure, actual)
            assertEquals(listOf(storeFailure), actual.suppressed.toList())
        }

        assertNull(state.active.value)
        assertEquals(SessionStatus.SignedOut, state.status.value)
        assertTrue(store.clearCalled)
    }

    @Test
    fun storeCancellationDuringClearTakesPriorityAndSuppressesObserverFailure() = runBlocking {
        val observerFailure = IllegalStateException("observer failed")
        val cancellation = CancellationException("store clear cancelled")
        val state = SessionState()
        val store = FakeSessionStore(clearError = cancellation)
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle())
        state.observeActiveSession { session ->
            if (session == null) throw observerFailure
        }

        try {
            manager.clear()
            fail("store cancellation should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
            assertEquals(listOf(observerFailure), actual.suppressed.toList())
        }

        assertNull(state.active.value)
        assertEquals(SessionStatus.SignedOut, state.status.value)
        assertTrue(store.clearCalled)
    }

    @Test
    fun cleanupFailuresCannotReplaceOriginalReadFailure() = runBlocking {
        val readFailure = IOException("store read failed")
        val observerFailure = IllegalStateException("observer failed")
        val storeCleanupFailure = IOException("store clear failed")
        val state = SessionState()
        val store = FakeSessionStore()
        val manager = SessionManager(store, state)
        manager.install(sampleTokenBundle())
        state.observeActiveSession { session ->
            if (session == null) throw observerFailure
        }
        store.readError = readFailure
        store.clearError = storeCleanupFailure

        val result = manager.acquireRefreshLease()

        val error = (result as AppResult.Failure).error
        assertEquals("SESSION_STORE_READ_FAILED", error.code)
        assertSame(readFailure, error.cause)
        assertEquals(
            listOf(observerFailure, storeCleanupFailure),
            readFailure.suppressed.toList(),
        )
        assertNull(state.active.value)
        assertEquals(SessionStatus.SignedOut, state.status.value)
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
    fun cancellationWhileWaitingToInstallDoesNotClearSessionPublishedAheadOfIt() = runBlocking {
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
            assertEquals("blocking-access-token", state.active.value?.accessToken)
            assertFalse(store.clearCalled)
            assertEquals("blocking-refresh-token", store.lastWritten?.refreshToken)
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
        user: User = User(
            id = "student-1",
            username = "student",
            role = UserRole.STUDENT,
            pointsBalance = 42,
        ),
    ) = TokenBundle(
        accessToken = accessToken,
        accessTokenExpiresAt = Instant.parse("2030-01-01T01:00:00Z"),
        refreshToken = refreshToken,
        refreshTokenExpiresAt = Instant.parse("2030-02-01T00:00:00Z"),
        user = user,
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
