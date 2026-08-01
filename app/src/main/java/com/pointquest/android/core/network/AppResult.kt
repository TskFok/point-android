package com.pointquest.android.core.network

import java.io.IOException
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
    else -> AppResult.Success(mapper(requireNotNull(body())))
}

fun IOException.toNetworkError(): AppError = AppError(
    httpStatus = null,
    code = "NETWORK_ERROR",
    message = "Network request failed",
    requestId = null,
    cause = this,
)
