package com.pointquest.android.core.network

import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class ApiErrorParserTest {
    @Test
    fun parsesStableErrorPayload() {
        val response = Response.error<Unit>(
            409,
            """{"code":"INSUFFICIENT_POINTS","message":"积分不足","requestId":"req_1","details":{"balance":7}}"""
                .toResponseBody("application/json".toMediaType()),
        )

        val error = ApiErrorParser(Moshi.Builder().build()).parse(response)

        assertEquals("INSUFFICIENT_POINTS", error.code)
        assertEquals("req_1", error.requestId)
        assertEquals(7.0, error.details["balance"])
    }

    @Test
    fun usesServerErrorForMalformedFiveHundredResponse() {
        val response = Response.error<Unit>(
            500,
            "not-json".toResponseBody("text/plain".toMediaType()),
        )

        val error = ApiErrorParser(Moshi.Builder().build()).parse(response)

        assertEquals("SERVER_ERROR", error.code)
        assertEquals(500, error.httpStatus)
    }

    @Test
    fun usesHttpErrorForMalformedNonFiveHundredResponse() {
        val response = Response.error<Unit>(
            400,
            "not-json".toResponseBody("text/plain".toMediaType()),
        )

        val error = ApiErrorParser(Moshi.Builder().build()).parse(response)

        assertEquals("HTTP_ERROR", error.code)
        assertEquals(400, error.httpStatus)
    }

    @Test
    fun parserNeverSwallowsJvmErrors() {
        val expected = AssertionError("fatal parser failure")
        val response = Response.error<Unit>(
            500,
            object : ResponseBody() {
                override fun contentType() = "application/json".toMediaType()
                override fun contentLength() = -1L
                override fun source(): BufferedSource = throw expected
            },
        )

        try {
            ApiErrorParser(Moshi.Builder().build()).parse(response)
            fail("JVM Error must escape API error parsing")
        } catch (actual: AssertionError) {
            assertEquals(expected, actual)
        }
    }

    @Test
    fun returnsEmptyResponseErrorForSuccessfulResponseWithoutBody() {
        val result = Response.success<String>(null).toAppResult { it.length }

        val error = (result as AppResult.Failure).error
        assertEquals("EMPTY_RESPONSE", error.code)
        assertEquals(200, error.httpStatus)
    }

    @Test
    fun rethrowsCancellationFromResponseMapper() {
        try {
            Response.success("value").toAppResult { throw CancellationException("cancel") }
            fail("CancellationException should be rethrown")
        } catch (_: CancellationException) {
            // Expected: cancellation is control flow and must not become an AppError.
        }
    }

    @Test
    fun rethrowsOrdinaryMapperException() {
        val expected = IllegalStateException("mapping failed")

        try {
            Response.success("value").toAppResult { throw expected }
            fail("Mapper exception should be rethrown")
        } catch (actual: IllegalStateException) {
            assertEquals(expected, actual)
        }
    }

    @Test
    fun rethrowsMapperError() {
        val expected = AssertionError("programming error")

        try {
            Response.success("value").toAppResult { throw expected }
            fail("Mapper error should be rethrown")
        } catch (actual: AssertionError) {
            assertEquals(expected, actual)
        }
    }

    @Test
    fun mapsIoExceptionToNetworkErrorWithCause() {
        val cause = IOException("offline")

        val error = cause.toNetworkError()

        assertEquals("NETWORK_ERROR", error.code)
        assertEquals(null, error.httpStatus)
        assertEquals(cause, error.cause)
    }
}
