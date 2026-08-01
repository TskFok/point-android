package com.pointquest.android.core.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RetryExecutorTest {
    @Test
    fun retriesReuseSameKeyAndPayloadInstance() = runBlocking {
        val delayProvider = RecordingDelayProvider()
        val executor = RetryExecutor(
            delayProvider = delayProvider,
            jitterSource = RecordingJitterSource(0, 0),
            idempotencyKeyFactory = IdempotencyKeyFactory { "key-1" },
        )
        val payload = mapOf("selectedOptionId" to "o2")
        val seen = mutableListOf<Pair<String, Any>>()

        val result = executor.executeIdempotent(payload) { operation ->
            seen += operation.key to operation.payload
            if (seen.size < 3) failure(code = "CONCURRENT_MODIFICATION") else AppResult.Success("ok")
        }

        assertEquals("ok", (result as AppResult.Success).value)
        assertEquals(1, seen.map { it.first }.distinct().size)
        assertTrue(seen.all { it.second === payload })
        assertEquals(listOf(250L, 500L), delayProvider.delays)
    }

    @Test
    fun readRetriesOnlyNetworkErrorsAndFiveHundreds() = runBlocking {
        val delayProvider = RecordingDelayProvider()
        val executor = RetryExecutor(
            delayProvider = delayProvider,
            jitterSource = RecordingJitterSource(0, 0),
        )
        var attempts = 0

        val result = executor.executeRead {
            attempts++
            when (attempts) {
                1 -> failure(code = "NETWORK_ERROR")
                2 -> failure(httpStatus = 503, code = "SERVICE_UNAVAILABLE")
                else -> AppResult.Success("loaded")
            }
        }

        assertEquals("loaded", (result as AppResult.Success).value)
        assertEquals(3, attempts)
        assertEquals(listOf(250L, 500L), delayProvider.delays)
    }

    @Test
    fun delayAddsOnlyInjectedJitterWithinConfiguredRange() = runBlocking {
        val delayProvider = RecordingDelayProvider()
        val jitterSource = RecordingJitterSource(0, 100)
        val executor = RetryExecutor(
            delayProvider = delayProvider,
            jitterSource = jitterSource,
        )
        var attempts = 0

        val result = executor.executeRead {
            attempts++
            if (attempts < 3) failure(code = "NETWORK_ERROR") else AppResult.Success("ok")
        }

        assertEquals("ok", (result as AppResult.Success).value)
        assertEquals(listOf(250L, 600L), delayProvider.delays)
        assertEquals(listOf(100L, 100L), jitterSource.maximums)
    }

    @Test
    fun returnsLastFailureAfterMaximumAttempts() = runBlocking {
        val delayProvider = RecordingDelayProvider()
        val executor = RetryExecutor(
            delayProvider = delayProvider,
            jitterSource = RecordingJitterSource(0, 0),
        )
        var attempts = 0
        val last = failure(httpStatus = 503, code = "LAST_FAILURE")

        val result = executor.executeRead {
            attempts++
            if (attempts == 3) last else failure(httpStatus = 503, code = "TEMPORARY")
        }

        assertSame(last.error, (result as AppResult.Failure).error)
        assertEquals(3, attempts)
        assertEquals(listOf(250L, 500L), delayProvider.delays)
    }

    @Test
    fun idempotencyConflictAndClientErrorsAreNotRetried() = runBlocking {
        val delayProvider = RecordingDelayProvider()
        val executor = RetryExecutor(
            delayProvider = delayProvider,
            jitterSource = RecordingJitterSource(0, 0),
        )
        var conflictAttempts = 0
        var clientErrorAttempts = 0

        val conflict = executor.executeIdempotent("payload") {
            conflictAttempts++
            failure(httpStatus = 409, code = "IDEMPOTENCY_CONFLICT")
        }
        val clientError = executor.executeRead {
            clientErrorAttempts++
            failure(httpStatus = 400, code = "UNRECOGNIZED_CLIENT_ERROR")
        }

        assertEquals("IDEMPOTENCY_CONFLICT", (conflict as AppResult.Failure).error.code)
        assertEquals("UNRECOGNIZED_CLIENT_ERROR", (clientError as AppResult.Failure).error.code)
        assertEquals(1, conflictAttempts)
        assertEquals(1, clientErrorAttempts)
        assertTrue(delayProvider.delays.isEmpty())
    }

    @Test
    fun clientStatusOverridesRetryableErrorCodesExceptConcurrentModificationForWrites() = runBlocking {
        val delayProvider = RecordingDelayProvider()
        val executor = RetryExecutor(
            delayProvider = delayProvider,
            jitterSource = RecordingJitterSource(0, 0),
        )
        var readAttempts = 0
        var writeAttempts = 0
        var concurrentModificationAttempts = 0

        val read = executor.executeRead<Unit> {
            readAttempts++
            failure(httpStatus = 400, code = "NETWORK_ERROR")
        }
        val write = executor.executeIdempotent("payload") {
            writeAttempts++
            failure(httpStatus = 400, code = "NETWORK_ERROR")
        }
        val concurrentModification = executor.executeIdempotent("payload") {
            concurrentModificationAttempts++
            if (concurrentModificationAttempts < 3) {
                failure(httpStatus = 409, code = "CONCURRENT_MODIFICATION")
            } else {
                AppResult.Success("saved")
            }
        }

        assertEquals("NETWORK_ERROR", (read as AppResult.Failure).error.code)
        assertEquals("NETWORK_ERROR", (write as AppResult.Failure).error.code)
        assertEquals("saved", (concurrentModification as AppResult.Success).value)
        assertEquals(1, readAttempts)
        assertEquals(1, writeAttempts)
        assertEquals(3, concurrentModificationAttempts)
        assertEquals(listOf(250L, 500L), delayProvider.delays)
    }

    @Test
    fun authenticationErrorsAreNotRetried() = runBlocking {
        val delayProvider = RecordingDelayProvider()
        val executor = RetryExecutor(delayProvider = delayProvider)
        var attempts = 0

        val result = executor.executeIdempotent("payload") {
            attempts++
            failure(httpStatus = 401, code = "AUTH_TOKEN_EXPIRED")
        }

        assertEquals("AUTH_TOKEN_EXPIRED", (result as AppResult.Failure).error.code)
        assertEquals(1, attempts)
        assertTrue(delayProvider.delays.isEmpty())
    }

    @Test
    fun cancellationIsPropagatedWithoutWrappingOrRetrying() = runBlocking {
        val delayProvider = RecordingDelayProvider()
        val executor = RetryExecutor(delayProvider = delayProvider)
        val cancellation = CancellationException("cancelled")
        var attempts = 0

        try {
            executor.executeRead<Unit> {
                attempts++
                throw cancellation
            }
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }

        assertEquals(1, attempts)
        assertTrue(delayProvider.delays.isEmpty())
    }

    @Test
    fun thrownIoExceptionIsRetriedAsNetworkError() = runBlocking {
        val executor = RetryExecutor(
            delayProvider = RecordingDelayProvider(),
            jitterSource = RecordingJitterSource(0, 0),
        )
        var attempts = 0

        val result = executor.executeRead {
            attempts++
            if (attempts < 3) throw IOException("offline") else AppResult.Success("ok")
        }

        assertEquals("ok", (result as AppResult.Success).value)
        assertEquals(3, attempts)
    }

    @Test
    fun policyRejectsUnsupportedAttemptsAndInvalidDelays() {
        assertIllegalArgument { RetryPolicy(maxAttempts = 0) }
        assertIllegalArgument { RetryPolicy(maxAttempts = 4, baseDelaysMs = listOf(1, 2, 3)) }
        assertIllegalArgument { RetryPolicy(maxAttempts = 3, baseDelaysMs = listOf(1)) }
        assertIllegalArgument { RetryPolicy(maxAttempts = 2, baseDelaysMs = listOf(-1)) }
        assertIllegalArgument { RetryPolicy(maxAttempts = 1, baseDelaysMs = listOf(-1)) }
        assertIllegalArgument { RetryPolicy(maxJitterMs = -1) }
        assertIllegalArgument { RetryPolicy(maxJitterMs = 101) }
        assertIllegalArgument { RetryPolicy(baseDelaysMs = listOf(Long.MAX_VALUE, 500)) }
    }

    @Test
    fun policyAcceptsLargestDelayThatCanSafelyIncludeMaximumJitter() {
        val policy = RetryPolicy(
            maxAttempts = 2,
            baseDelaysMs = listOf(Long.MAX_VALUE - 100),
            maxJitterMs = 100,
        )

        assertEquals(Long.MAX_VALUE - 100, policy.baseDelaysMs.single())
        assertEquals(100, policy.maxJitterMs)
    }

    @Test
    fun jitterOutsideConfiguredRangeIsRejected() = runBlocking {
        val delayProvider = RecordingDelayProvider()
        val executor = RetryExecutor(
            policy = RetryPolicy(maxAttempts = 2, baseDelaysMs = listOf(1), maxJitterMs = 10),
            delayProvider = delayProvider,
            jitterSource = RecordingJitterSource(11),
        )

        try {
            executor.executeRead<Unit> { failure(code = "NETWORK_ERROR") }
            fail("Out-of-range jitter should be rejected")
        } catch (actual: IllegalStateException) {
            assertEquals("Jitter source returned a value outside the configured range.", actual.message)
        }

        assertTrue(delayProvider.delays.isEmpty())
    }

    private class RecordingDelayProvider : DelayProvider {
        val delays = mutableListOf<Long>()

        override suspend fun delay(milliseconds: Long) {
            delays += milliseconds
        }
    }

    private class RecordingJitterSource(vararg private val values: Long) : JitterSource {
        val maximums = mutableListOf<Long>()
        private var index = 0

        override fun nextLong(maxInclusive: Long): Long {
            maximums += maxInclusive
            return values.getOrElse(index++) { 0 }
        }
    }

    private fun failure(httpStatus: Int? = null, code: String): AppResult.Failure = AppResult.Failure(
        AppError(httpStatus, code, code, null),
    )

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("IllegalArgumentException should be thrown")
        } catch (_: IllegalArgumentException) {
        }
    }
}
