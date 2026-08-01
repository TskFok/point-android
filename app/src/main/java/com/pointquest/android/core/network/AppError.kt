package com.pointquest.android.core.network

data class AppError(
    val httpStatus: Int?,
    val code: String,
    val message: String,
    val requestId: String?,
    val details: Map<String, Any?> = emptyMap(),
    val cause: Throwable? = null,
)
