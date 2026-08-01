package com.pointquest.android.core.network

import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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
    fun rethrowsCancellationFromResponseMapper() {
        try {
            Response.success("value").toAppResult { throw CancellationException("cancel") }
            fail("CancellationException should be rethrown")
        } catch (_: CancellationException) {
            // Expected: cancellation is control flow and must not become an AppError.
        }
    }
}
