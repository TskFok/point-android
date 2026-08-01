package com.pointquest.android.data.gateway

import com.pointquest.android.core.network.ApiClients
import com.pointquest.android.core.network.AppResult
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import com.pointquest.android.generated.api.DefaultApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeneratedPublicAuthGatewayTest {
    @Test
    fun loginUsesAndroidTokenEndpointAndMapsExactGeneratedResponse() = runBlocking {
        val terminal = TerminalInterceptor(
            body = tokenJson("access-token", "refresh-token"),
            code = 201,
        )
        val gateway = gateway(terminal)

        val result = gateway.login("student", "pass1234")

        val bundle = (result as AppResult.Success).value
        assertEquals("/api/v1/auth/token", terminal.path)
        assertTrue(requireNotNull(terminal.requestBody).contains("\"username\":\"student\""))
        assertTrue(requireNotNull(terminal.requestBody).contains("\"password\":\"pass1234\""))
        assertEquals("access-token", bundle.accessToken)
        assertEquals(now.plusSeconds(120), bundle.accessTokenExpiresAt)
        assertEquals("refresh-token", bundle.refreshToken)
        assertEquals(Instant.parse("2030-02-01T00:00:00Z"), bundle.refreshTokenExpiresAt)
        assertEquals("student", bundle.user.username)
    }

    @Test
    fun refreshUsesBodyTokenAndMissingResponseFailsClosed() = runBlocking {
        val terminal = TerminalInterceptor(body = null, code = 204)
        val gateway = gateway(terminal)

        val result = gateway.refresh("refresh-secret")

        assertEquals("/api/v1/auth/refresh", terminal.path)
        assertTrue(requireNotNull(terminal.requestBody).contains("refresh-secret"))
        assertEquals("EMPTY_RESPONSE", (result as AppResult.Failure).error.code)
    }

    @Test
    fun stableAuthErrorIsParsedWithoutRetry() = runBlocking {
        val terminal = TerminalInterceptor(
            body = """{"code":"AUTH_INVALID_CREDENTIALS","message":"bad","requestId":"req-1"}""",
            code = 401,
        )
        val gateway = gateway(terminal)

        val result = gateway.login("student", "wrong")

        val error = (result as AppResult.Failure).error
        assertEquals(401, error.httpStatus)
        assertEquals("AUTH_INVALID_CREDENTIALS", error.code)
        assertEquals(1, terminal.calls)
    }

    @Test
    fun malformedRefreshResponseBecomesStableNetworkFailure() = runBlocking {
        val gateway = gateway(TerminalInterceptor(body = "{not-json", code = 201))

        val result = gateway.refresh("refresh-secret")

        val error = (result as AppResult.Failure).error
        assertEquals("NETWORK_ERROR", error.code)
        assertTrue(error.cause is Exception)
    }

    @Test
    fun validJsonMissingRequiredTokenFieldBecomesInvalidResponse() = runBlocking {
        val missingAccessToken = """
            {
              "accessTokenExpiresIn":120,
              "refreshToken":"refresh-token",
              "refreshTokenExpiresAt":"2030-02-01T00:00:00Z",
              "user":{"id":"student-1","pointsBalance":42,"role":"STUDENT","username":"student"}
            }
        """.trimIndent()
        val gateway = gateway(TerminalInterceptor(body = missingAccessToken, code = 201))

        val result = gateway.login("student", "pass1234")

        val error = (result as AppResult.Failure).error
        assertEquals("INVALID_RESPONSE", error.code)
        assertTrue(error.cause is RuntimeException)
    }

    @Test
    fun invalidRefreshExpiryTimeBecomesInvalidResponse() = runBlocking {
        val invalidTime = tokenJson("access-token", "refresh-token")
            .replace("2030-02-01T00:00:00Z", "not-a-time")
        val gateway = gateway(TerminalInterceptor(body = invalidTime, code = 201))

        val result = gateway.refresh("old-refresh")

        val error = (result as AppResult.Failure).error
        assertEquals("INVALID_RESPONSE", error.code)
        assertTrue(error.cause is RuntimeException)
    }

    @Test
    fun ioFailureBecomesNetworkErrorAndCancellationIsRethrown() = runBlocking {
        val io = IOException("offline")
        val ioResult = gateway(TerminalInterceptor(failure = io)).login("student", "pass1234")
        val ioError = (ioResult as AppResult.Failure).error
        assertEquals("NETWORK_ERROR", ioError.code)
        assertTrue(ioError.cause is IOException)
        assertEquals("offline", ioError.cause?.message)

        val cancellation = CancellationException("cancelled")
        try {
            GeneratedPublicAuthGateway(cancellingApi(cancellation), clock)
                .login("student", "pass1234")
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun cancellingApi(cancellation: CancellationException): DefaultApi =
        Proxy.newProxyInstance(
            DefaultApi::class.java.classLoader,
            arrayOf(DefaultApi::class.java),
        ) { _, _, args ->
            val continuation = requireNotNull(args).last() as Continuation<Any?>
            continuation.resumeWith(Result.failure(cancellation))
            COROUTINE_SUSPENDED
        } as DefaultApi

    private fun gateway(terminal: Interceptor): GeneratedPublicAuthGateway {
        val builder = ApiClients.publicBuilder().addInterceptor(terminal)
        return GeneratedPublicAuthGateway(
            ApiClients.defaultApi("https://example.test/", builder),
            clock,
        )
    }

    private class TerminalInterceptor(
        private val body: String? = null,
        private val code: Int = 200,
        private val failure: Throwable? = null,
    ) : Interceptor {
        var calls = 0
        var path: String? = null
        var requestBody: String? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            calls++
            val request = chain.request()
            path = request.url.encodedPath
            requestBody = request.body?.let { body ->
                Buffer().also(body::writeTo).readUtf8()
            }
            failure?.let { throw it }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code < 400) "OK" else "Error")
                .body((body ?: "").toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private companion object {
        val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

        fun tokenJson(accessToken: String, refreshToken: String) = """
            {
              "accessToken":"$accessToken",
              "accessTokenExpiresIn":120,
              "refreshToken":"$refreshToken",
              "refreshTokenExpiresAt":"2030-02-01T00:00:00Z",
              "user":{"id":"student-1","pointsBalance":42,"role":"STUDENT","username":"student"}
            }
        """.trimIndent()
    }
}
