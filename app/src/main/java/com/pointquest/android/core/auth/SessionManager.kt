package com.pointquest.android.core.auth

import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface RefreshMaterialSnapshot {
    val epoch: Long

    data class Available(
        override val epoch: Long,
        val storedSession: StoredRefreshSession,
    ) : RefreshMaterialSnapshot

    data class Missing(
        override val epoch: Long,
    ) : RefreshMaterialSnapshot
}

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
    private var refreshMaterialValid = true

    internal suspend fun acquireRefreshLease(): AppResult<RefreshMaterialSnapshot> = mutex.withLock {
        if (!refreshMaterialValid) {
            return@withLock AppResult.Success(RefreshMaterialSnapshot.Missing(epoch))
        }
        try {
            val stored = store.read()
            AppResult.Success(
                if (stored == null) {
                    RefreshMaterialSnapshot.Missing(epoch)
                } else {
                    RefreshMaterialSnapshot.Available(epoch, stored)
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
        when (val snapshot = acquireRefreshLease()) {
            is AppResult.Failure -> snapshot
            is AppResult.Success -> AppResult.Success(
                (snapshot.value as? RefreshMaterialSnapshot.Available)?.storedSession,
            )
        }

    suspend fun install(bundle: TokenBundle): AppResult<ActiveSession> =
        mutex.withLock { installLocked(bundle) }

    internal suspend fun commitRefresh(
        lease: RefreshMaterialSnapshot.Available,
        bundle: TokenBundle,
    ): AppResult<RefreshCommit> = mutex.withLock {
        if (epoch != lease.epoch) {
            return@withLock AppResult.Success(RefreshCommit.Stale(state.active.value))
        }
        when (val installed = installLocked(bundle)) {
            is AppResult.Failure -> installed
            is AppResult.Success -> AppResult.Success(RefreshCommit.Installed(installed.value))
        }
    }

    internal suspend fun invalidateRefreshLease(
        lease: RefreshMaterialSnapshot.Available,
    ): Boolean = clearIfEpochMatches(lease.epoch)

    internal suspend fun clearIfEpochMatches(sampledEpoch: Long): Boolean = mutex.withLock {
        if (epoch != sampledEpoch) return@withLock false
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
            cleanupLockedPreservingFailure()
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
