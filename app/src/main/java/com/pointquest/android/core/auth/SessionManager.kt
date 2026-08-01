package com.pointquest.android.core.auth

import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class RefreshLease(
    val epoch: Long,
    val storedSession: StoredRefreshSession,
)

internal sealed interface RefreshCommit {
    data class Installed(val session: ActiveSession) : RefreshCommit
    data class Stale(val current: ActiveSession?) : RefreshCommit
}

class SessionManager(
    private val store: SessionStore,
    private val state: SessionState,
) {
    private val mutex = Mutex()
    private var epoch = state.active.value?.generation ?: 0L
    @Volatile
    private var refreshMaterialValid = true

    internal suspend fun acquireRefreshLease(): AppResult<RefreshLease?> = mutex.withLock {
        if (!refreshMaterialValid) return@withLock AppResult.Success(null)
        try {
            AppResult.Success(
                store.read()?.let { stored ->
                    RefreshLease(epoch = epoch, storedSession = stored)
                },
            )
        } catch (cancellation: CancellationException) {
            cleanupLockedPreservingFailure()
            throw cancellation
        } catch (failure: Exception) {
            cleanupLockedPreservingFailure()
            AppResult.Failure(sessionError(SESSION_STORE_READ_FAILED, failure))
        }
    }

    internal suspend fun readStoredRefreshSession(): AppResult<StoredRefreshSession?> =
        when (val lease = acquireRefreshLease()) {
            is AppResult.Failure -> lease
            is AppResult.Success -> AppResult.Success(lease.value?.storedSession)
        }

    suspend fun install(bundle: TokenBundle): AppResult<ActiveSession> = try {
        mutex.withLock { installLocked(bundle) }
    } catch (cancellation: CancellationException) {
        cleanupAfterCancellation()
        throw cancellation
    }

    internal suspend fun commitRefresh(
        lease: RefreshLease,
        bundle: TokenBundle,
    ): AppResult<RefreshCommit> = try {
        mutex.withLock {
            if (epoch != lease.epoch) {
                return@withLock AppResult.Success(RefreshCommit.Stale(state.active.value))
            }
            when (val installed = installLocked(bundle)) {
                is AppResult.Failure -> installed
                is AppResult.Success -> AppResult.Success(RefreshCommit.Installed(installed.value))
            }
        }
    } catch (cancellation: CancellationException) {
        cleanupAfterCancellation()
        throw cancellation
    }

    internal suspend fun invalidateRefreshLease(lease: RefreshLease): Boolean = mutex.withLock {
        if (epoch != lease.epoch) return@withLock false
        cleanupLockedPreservingFailure()
        true
    }

    suspend fun clear(): AppResult<Unit> = mutex.withLock {
        invalidateLocked()
        try {
            store.clear()
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            AppResult.Failure(sessionError(SESSION_STORE_CLEAR_FAILED, failure))
        }
    }

    private suspend fun installLocked(bundle: TokenBundle): AppResult<ActiveSession> {
        val storedSession = StoredRefreshSession(
            refreshToken = bundle.refreshToken,
            expiresAt = bundle.refreshTokenExpiresAt,
        )

        try {
            store.write(storedSession)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            cleanupLockedPreservingFailure()
            return AppResult.Failure(sessionError(SESSION_STORE_WRITE_FAILED, failure))
        }

        epoch += 1
        refreshMaterialValid = true
        val activeSession = ActiveSession(
            user = bundle.user,
            accessToken = bundle.accessToken,
            accessTokenExpiresAt = bundle.accessTokenExpiresAt,
            generation = epoch,
        )
        state.publish(activeSession)
        return AppResult.Success(activeSession)
    }

    private suspend fun cleanupAfterCancellation() {
        refreshMaterialValid = false
        state.clear()
        withContext(NonCancellable) {
            mutex.withLock { cleanupLockedPreservingFailure() }
        }
    }

    private suspend fun cleanupLockedPreservingFailure() {
        invalidateLocked()
        withContext(NonCancellable) {
            try {
                store.clear()
            } catch (_: Throwable) {
                // Best effort: cleanup must never replace the original failure.
            }
        }
    }

    private fun invalidateLocked() {
        epoch += 1
        refreshMaterialValid = false
        state.clear()
    }

    private fun sessionError(code: String, cause: Throwable) = AppError(
        httpStatus = null,
        code = code,
        message = "Secure session storage failed",
        requestId = null,
        cause = cause,
    )

    private companion object {
        const val SESSION_STORE_READ_FAILED = "SESSION_STORE_READ_FAILED"
        const val SESSION_STORE_WRITE_FAILED = "SESSION_STORE_WRITE_FAILED"
        const val SESSION_STORE_CLEAR_FAILED = "SESSION_STORE_CLEAR_FAILED"
    }
}
