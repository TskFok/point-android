package com.pointquest.android.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import retrofit2.Response

class ApiErrorParser(moshi: Moshi) {
    private val errorBodyAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java,
        ),
    )

    fun parse(response: Response<*>): AppError {
        val payload = response.errorBody()
            ?.let { body -> runCatching { errorBodyAdapter.fromJson(body.string()) }.getOrNull() }
        val code = payload?.get("code") as? String
        val message = payload?.get("message") as? String
        if (!code.isNullOrBlank() && !message.isNullOrBlank()) {
            @Suppress("UNCHECKED_CAST")
            val details = payload["details"] as? Map<String, Any?> ?: emptyMap()
            return AppError(
                httpStatus = response.code(),
                code = code,
                message = message,
                requestId = payload["requestId"] as? String,
                details = details,
            )
        }

        return AppError(
            httpStatus = response.code(),
            code = if (response.code() >= 500) "SERVER_ERROR" else "HTTP_ERROR",
            message = if (response.code() >= 500) "Server error" else "Request failed",
            requestId = null,
        )
    }

    companion object {
        val default: ApiErrorParser = ApiErrorParser(Moshi.Builder().build())
    }
}
