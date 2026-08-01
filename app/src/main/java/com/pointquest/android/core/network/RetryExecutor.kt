package com.pointquest.android.core.network

import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Executes only GET calls and idempotent writes. Token refresh deliberately uses its own flow and
 * must not be passed through this executor.
 */
class RetryExecutor(
    private val policy: RetryPolicy = RetryPolicy(),
    private val delayProvider: DelayProvider = defaultDelayProvider,
    private val jitterSource: JitterSource = defaultJitterSource,
    private val idempotencyKeyFactory: IdempotencyKeyFactory = defaultIdempotencyKeyFactory,
) {
    suspend fun <T> executeRead(operation: suspend () -> AppResult<T>): AppResult<T> =
        execute(operation, ::isReadRetryable)

    suspend fun <P, T> executeIdempotent(
        payload: P,
        operation: suspend (IdempotentOperation<P>) -> AppResult<T>,
    ): AppResult<T> {
        val idempotentOperation = IdempotentOperation(idempotencyKeyFactory.create(), payload)
        return execute({ operation(idempotentOperation) }, ::isIdempotentRetryable)
    }

    private suspend fun <T> execute(
        operation: suspend () -> AppResult<T>,
        retryable: (AppError) -> Boolean,
    ): AppResult<T> {
        repeat(policy.maxAttempts) { attemptIndex ->
            when (val result = invoke(operation)) {
                is AppResult.Success -> return result
                is AppResult.Failure -> {
                    if (attemptIndex == policy.maxAttempts - 1 || !retryable(result.error)) {
                        return result
                    }
                    waitBeforeRetry(attemptIndex)
                }
            }
        }
        error("Retry loop must return from an attempted operation.")
    }

    private suspend fun <T> invoke(operation: suspend () -> AppResult<T>): AppResult<T> = try {
        operation()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: IOException) {
        AppResult.Failure(failure.toNetworkError())
    }

    private suspend fun waitBeforeRetry(retryIndex: Int) {
        val jitter = jitterSource.nextLong(policy.maxJitterMs)
        check(jitter in 0..policy.maxJitterMs) {
            "Jitter source returned a value outside the configured range."
        }
        delayProvider.delay(policy.baseDelaysMs[retryIndex] + jitter)
    }

    private fun isReadRetryable(error: AppError): Boolean =
        !error.isNeverRetryable() &&
            !error.isClientError() &&
            (error.code == NETWORK_ERROR || error.httpStatus in 500..599)

    private fun isIdempotentRetryable(error: AppError): Boolean =
        when {
            error.isNeverRetryable() -> false
            error.isClientError() -> error.code == CONCURRENT_MODIFICATION
            else ->
                error.code == CONCURRENT_MODIFICATION ||
                    error.code == NETWORK_ERROR ||
                    error.httpStatus in 500..599
        }

    private fun AppError.isNeverRetryable(): Boolean =
        code == IDEMPOTENCY_CONFLICT ||
            code.startsWith("AUTH_") ||
            httpStatus == 401 ||
            httpStatus == 403

    private fun AppError.isClientError(): Boolean = httpStatus in 400..499

    private companion object {
        const val NETWORK_ERROR = "NETWORK_ERROR"
        const val CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION"
        const val IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT"
    }
}
