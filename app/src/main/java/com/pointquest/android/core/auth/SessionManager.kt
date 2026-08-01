package com.pointquest.android.core.auth

import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionManager(
    private val store: SessionStore,
    private val state: SessionState,
) {
    private val mutex = Mutex()
    private var generation = state.active.value?.generation ?: 0L

    suspend fun install(bundle: TokenBundle): AppResult<ActiveSession> = try {
        mutex.withLock {
            val storedSession = StoredRefreshSession(
                refreshToken = bundle.refreshToken,
                expiresAt = bundle.refreshTokenExpiresAt,
            )

            try {
                store.write(storedSession)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                state.clear()
                clearStoreAfterInstallFailure()
                return@withLock AppResult.Failure(sessionError(SESSION_STORE_WRITE_FAILED, failure))
            }

            val activeSession = ActiveSession(
                user = bundle.user,
                accessToken = bundle.accessToken,
                accessTokenExpiresAt = bundle.accessTokenExpiresAt,
                generation = generation + 1,
            )
            generation = activeSession.generation
            state.publish(activeSession)
            AppResult.Success(activeSession)
        }
    } catch (cancellation: CancellationException) {
        state.clear()
        withContext(NonCancellable) {
            mutex.withLock {
                state.clear()
                try {
                    store.clear()
                } catch (_: Throwable) {
                    // Best effort: cleanup must never replace the original cancellation.
                }
            }
        }
        throw cancellation
    }

    suspend fun clear(): AppResult<Unit> = mutex.withLock {
        state.clear()
        try {
            store.clear()
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            AppResult.Failure(sessionError(SESSION_STORE_CLEAR_FAILED, failure))
        }
    }

    private suspend fun clearStoreAfterInstallFailure() {
        try {
            store.clear()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Best effort: preserve the original write failure as the stable result.
        }
    }

    private fun sessionError(code: String, cause: Throwable) = AppError(
        httpStatus = null,
        code = code,
        message = "Secure session storage failed",
        requestId = null,
        cause = cause,
    )

    private companion object {
        const val SESSION_STORE_WRITE_FAILED = "SESSION_STORE_WRITE_FAILED"
        const val SESSION_STORE_CLEAR_FAILED = "SESSION_STORE_CLEAR_FAILED"
    }
}
