package com.pointquest.android.core.network

import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

fun <T, R> Response<T>.toAppResult(mapper: (T) -> R): AppResult<R> = when {
    !isSuccessful -> AppResult.Failure(ApiErrorParser.default.parse(this))
    body() == null -> AppResult.Failure(
        AppError(
            httpStatus = code(),
            code = "EMPTY_RESPONSE",
            message = "Response body is empty",
            requestId = null,
        ),
    )
    else -> try {
        AppResult.Success(mapper(requireNotNull(body())))
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Throwable) {
        AppResult.Failure(exception.toNetworkError())
    }
}

fun Throwable.toNetworkError(): AppError = AppError(
    httpStatus = null,
    code = "NETWORK_ERROR",
    message = "Network request failed",
    requestId = null,
    cause = this,
)
