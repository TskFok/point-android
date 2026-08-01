package com.pointquest.android.core.network

import kotlin.random.Random
import kotlinx.coroutines.delay as coroutineDelay

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelaysMs: List<Long> = listOf(250, 500),
    val maxJitterMs: Long = 100,
) {
    init {
        require(maxAttempts in 1..3) { "maxAttempts must be between 1 and 3." }
        require(baseDelaysMs.size >= maxAttempts - 1) {
            "baseDelaysMs must provide a delay for every retry."
        }
        require(baseDelaysMs.all { it >= 0 }) { "baseDelaysMs cannot contain negative values." }
        require(maxJitterMs >= 0) { "maxJitterMs cannot be negative." }
    }
}

fun interface DelayProvider {
    suspend fun delay(milliseconds: Long)
}

fun interface JitterSource {
    /** Returns an inclusive value between zero and [maxInclusive]. */
    fun nextLong(maxInclusive: Long): Long
}

internal val defaultDelayProvider = DelayProvider { milliseconds -> coroutineDelay(milliseconds) }

internal val defaultJitterSource = JitterSource { maxInclusive ->
    if (maxInclusive == 0L) 0L else Random.Default.nextLong(0, maxInclusive + 1)
}
