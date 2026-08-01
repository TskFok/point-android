package com.pointquest.android.core.auth

import com.pointquest.android.core.model.UserRole
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

internal data class RefreshOutcome(
    val session: ActiveSession,
    val refreshed: Boolean,
)

class RefreshCoordinator(
    private val gateway: PublicAuthGateway,
    private val sessionManager: SessionManager,
    private val sessionState: SessionState,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()

    suspend fun refresh(force: Boolean, observedGeneration: Long): AppResult<ActiveSession> =
        when (val outcome = refreshWithOutcome(force, observedGeneration)) {
            is AppResult.Failure -> outcome
            is AppResult.Success -> AppResult.Success(outcome.value.session)
        }

    internal suspend fun refreshWithOutcome(
        force: Boolean,
        observedGeneration: Long,
    ): AppResult<RefreshOutcome> {
        currentAfterAnotherRefresh(observedGeneration)?.let {
            return AppResult.Success(RefreshOutcome(it, refreshed = true))
        }
        val current = sessionState.active.value
        if (!force && current != null && current.accessTokenExpiresAt.isAfter(clock.instant().plusSeconds(30))) {
            return AppResult.Success(RefreshOutcome(current, refreshed = false))
        }

        return mutex.withLock {
            currentAfterAnotherRefresh(observedGeneration)?.let {
                return@withLock AppResult.Success(RefreshOutcome(it, refreshed = true))
            }
            val lockedCurrent = sessionState.active.value
            if (!force && lockedCurrent != null &&
                lockedCurrent.accessTokenExpiresAt.isAfter(clock.instant().plusSeconds(30))
            ) {
                return@withLock AppResult.Success(RefreshOutcome(lockedCurrent, refreshed = false))
            }

            val lease = when (val leaseResult = sessionManager.acquireRefreshLease()) {
                is AppResult.Success -> leaseResult.value
                is AppResult.Failure -> return@withLock leaseResult
            } ?: run {
                sessionManager.clear()
                return@withLock AppResult.Failure(noSessionError())
            }
            val stored = lease.storedSession

            if (!stored.expiresAt.isAfter(clock.instant())) {
                sessionManager.invalidateRefreshLease(lease)
                return@withLock AppResult.Failure(refreshExpiredError())
            }

            val refreshed = try {
                gateway.refresh(stored.refreshToken)
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    try {
                        sessionManager.invalidateRefreshLease(lease)
                    } catch (_: Throwable) {
                        // Cleanup must not replace the original cancellation.
                    }
                }
                throw cancellation
            } catch (failure: IOException) {
                val invalidated = sessionManager.invalidateRefreshLease(lease)
                if (!invalidated) return@withLock AppResult.Failure(sessionChangedError())
                return@withLock AppResult.Failure(failure.toNetworkError())
            } catch (failure: RuntimeException) {
                val invalidated = sessionManager.invalidateRefreshLease(lease)
                if (!invalidated) return@withLock AppResult.Failure(sessionChangedError())
                return@withLock AppResult.Failure(invalidResponseError(failure))
            }
            when (refreshed) {
                is AppResult.Failure -> {
                    if (sessionManager.invalidateRefreshLease(lease)) {
                        refreshed
                    } else {
                        AppResult.Failure(sessionChangedError())
                    }
                }
                is AppResult.Success -> {
                    if (refreshed.value.user.role != UserRole.STUDENT) {
                        return@withLock if (sessionManager.invalidateRefreshLease(lease)) {
                            AppResult.Failure(forbiddenError())
                        } else {
                            AppResult.Failure(sessionChangedError())
                        }
                    }
                    when (val committed = sessionManager.commitRefresh(lease, refreshed.value)) {
                        is AppResult.Failure -> committed
                        is AppResult.Success -> when (val outcome = committed.value) {
                            is RefreshCommit.Installed -> AppResult.Success(
                                RefreshOutcome(outcome.session, refreshed = true),
                            )
                            is RefreshCommit.Stale -> AppResult.Failure(sessionChangedError())
                        }
                    }
                }
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

    private fun sessionChangedError() = AppError(
        httpStatus = null,
        code = "AUTH_SESSION_CHANGED",
        message = "Authentication session changed during refresh",
        requestId = null,
    )

    private fun forbiddenError() = AppError(
        httpStatus = 403,
        code = "FORBIDDEN",
        message = "Student account required",
        requestId = null,
    )

    private fun invalidResponseError(cause: RuntimeException) = AppError(
        httpStatus = null,
        code = "INVALID_RESPONSE",
        message = "Server response is invalid",
        requestId = null,
        cause = cause,
    )
}
