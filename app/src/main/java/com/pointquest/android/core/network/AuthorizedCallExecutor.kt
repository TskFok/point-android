package com.pointquest.android.core.network

import com.pointquest.android.core.auth.RefreshCoordinator
import com.pointquest.android.core.auth.SessionState
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CancellationException

class AuthorizedCallExecutor(
    private val sessionState: SessionState,
    private val refreshCoordinator: RefreshCoordinator,
    @Suppress("UNUSED_PARAMETER") private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun <T> execute(call: suspend () -> AppResult<T>): AppResult<T> =
        executeOperation { execute(call) }

    suspend fun <T> executeOperation(
        operation: suspend AuthorizedOperation.() -> AppResult<T>,
    ): AppResult<T> = AuthorizedOperation().operation()

    inner class AuthorizedOperation internal constructor() {
        private var preflightCompleted = false
        private var authRecoveryUsed = false
        private var remainingBusinessCalls = MAX_BUSINESS_CALLS
        private var lastFailure: AppResult.Failure? = null

        suspend fun <T> execute(call: suspend () -> AppResult<T>): AppResult<T> {
            if (!preflightCompleted) {
                val observedBeforePreflight = sessionState.active.value?.generation ?: 0L
                when (val preflight = refreshCoordinator.refreshWithOutcome(false, observedBeforePreflight)) {
                    is AppResult.Failure -> return preflight
                    is AppResult.Success -> authRecoveryUsed = preflight.value.refreshed
                }
                preflightCompleted = true
            }

            val requestGeneration = sessionState.active.value?.generation ?: 0L
            val first = invokeWithinBudget(call) ?: return requireNotNull(lastFailure)
            if (!first.isExpiredAccessToken() || authRecoveryUsed) return first

            authRecoveryUsed = true
            return when (val refreshed = refreshCoordinator.refreshWithOutcome(true, requestGeneration)) {
                is AppResult.Failure -> refreshed
                is AppResult.Success -> invokeWithinBudget(call) ?: first
            }
        }

        private suspend fun <T> invokeWithinBudget(call: suspend () -> AppResult<T>): AppResult<T>? {
            if (remainingBusinessCalls == 0) return null
            remainingBusinessCalls -= 1
            return invoke(call).also { result ->
                if (result is AppResult.Failure) lastFailure = result
            }
        }

        private fun AppResult<*>.isExpiredAccessToken(): Boolean =
            this is AppResult.Failure &&
                error.httpStatus == 401 &&
                error.code == "AUTH_TOKEN_EXPIRED"
    }

    private suspend fun <T> invoke(call: suspend () -> AppResult<T>): AppResult<T> = try {
        call()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: IOException) {
        AppResult.Failure(failure.toNetworkError())
    }

    private companion object {
        const val MAX_BUSINESS_CALLS = 3
    }
}
