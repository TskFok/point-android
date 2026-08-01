package com.pointquest.android.core.auth

import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.toNetworkError
import com.pointquest.android.data.gateway.PublicAuthGateway
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RefreshCoordinator(
    private val gateway: PublicAuthGateway,
    private val sessionManager: SessionManager,
    private val sessionState: SessionState,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()

    suspend fun refresh(force: Boolean, observedGeneration: Long): AppResult<ActiveSession> {
        currentAfterAnotherRefresh(observedGeneration)?.let { return AppResult.Success(it) }
        val current = sessionState.active.value
        if (!force && current != null && current.accessTokenExpiresAt.isAfter(clock.instant().plusSeconds(30))) {
            return AppResult.Success(current)
        }

        return mutex.withLock {
            currentAfterAnotherRefresh(observedGeneration)?.let { return@withLock AppResult.Success(it) }
            val lockedCurrent = sessionState.active.value
            if (!force && lockedCurrent != null &&
                lockedCurrent.accessTokenExpiresAt.isAfter(clock.instant().plusSeconds(30))
            ) {
                return@withLock AppResult.Success(lockedCurrent)
            }

            val stored = when (val storedResult = sessionManager.readStoredRefreshSession()) {
                is AppResult.Success -> storedResult.value
                is AppResult.Failure -> return@withLock storedResult
            } ?: run {
                sessionManager.clear()
                return@withLock AppResult.Failure(noSessionError())
            }

            if (!stored.expiresAt.isAfter(clock.instant())) {
                sessionManager.clear()
                return@withLock AppResult.Failure(refreshExpiredError())
            }

            val refreshed = try {
                gateway.refresh(stored.refreshToken)
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    try {
                        sessionManager.clear()
                    } catch (_: Throwable) {
                        // Cleanup must not replace the original cancellation.
                    }
                }
                throw cancellation
            } catch (failure: IOException) {
                sessionManager.clear()
                AppResult.Failure(failure.toNetworkError())
            }
            when (refreshed) {
                is AppResult.Failure -> {
                    sessionManager.clear()
                    refreshed
                }
                is AppResult.Success -> sessionManager.install(refreshed.value)
            }
        }
    }

    private fun currentAfterAnotherRefresh(observedGeneration: Long): ActiveSession? =
        sessionState.active.value?.takeIf { it.generation != observedGeneration }

    private fun noSessionError() = AppError(
        httpStatus = null,
        code = "AUTH_SESSION_MISSING",
        message = "No authenticated session",
        requestId = null,
    )

    private fun refreshExpiredError() = AppError(
        httpStatus = 401,
        code = "AUTH_REFRESH_EXPIRED",
        message = "Refresh token has expired",
        requestId = null,
    )
}
