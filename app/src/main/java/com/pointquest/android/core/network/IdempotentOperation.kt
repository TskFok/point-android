package com.pointquest.android.core.network

import java.util.UUID

data class IdempotentOperation<T>(
    val key: String,
    val payload: T,
)

fun interface IdempotencyKeyFactory {
    fun create(): String
}

internal val defaultIdempotencyKeyFactory = IdempotencyKeyFactory { UUID.randomUUID().toString() }
