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
    suspend fun <T> execute(call: suspend () -> AppResult<T>): AppResult<T> {
        val observedBeforePreflight = sessionState.active.value?.generation ?: 0L
        when (val preflight = refreshCoordinator.refresh(false, observedBeforePreflight)) {
            is AppResult.Failure -> return preflight
            is AppResult.Success -> Unit
        }

        val requestGeneration = sessionState.active.value?.generation ?: 0L
        val first = invoke(call)
        if (first !is AppResult.Failure ||
            first.error.httpStatus != 401 ||
            first.error.code != "AUTH_TOKEN_EXPIRED"
        ) {
            return first
        }

        return when (val refreshed = refreshCoordinator.refresh(true, requestGeneration)) {
            is AppResult.Failure -> refreshed
            is AppResult.Success -> invoke(call)
        }
    }

    private suspend fun <T> invoke(call: suspend () -> AppResult<T>): AppResult<T> = try {
        call()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: IOException) {
        AppResult.Failure(failure.toNetworkError())
    }
}
