package com.pointquest.android.core.network

import com.pointquest.android.core.auth.SessionManager
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStore
import com.pointquest.android.core.auth.StoredRefreshSession
import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import java.time.Instant
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientsTest {
    @Test
    fun constructedClientsKeepPublicAndProtectedInstancesSeparated() {
        val clients = ApiClients("https://example.test/", SessionState())

        assertNotSame(clients.publicHttpClient, clients.protectedHttpClient)
        assertEquals(2, clients.publicHttpClient.interceptors.size)
        assertEquals(2, clients.protectedHttpClient.interceptors.size)
        assertFalse(clients.publicHttpClient.interceptors.any { it is BearerInterceptor })
        assertTrue(clients.protectedHttpClient.interceptors.any { it is BearerInterceptor })
    }

    @Test
    fun constructedClientsReadTheCurrentHostProviderWithoutChangingRequestPath() {
        var currentHost = "https://first.example.test/"
        val clients = ApiClients(
            baseUrl = "https://initial.example.test/",
            sessionState = SessionState(),
            hostProvider = { currentHost },
        )
        val request = Request.Builder()
            .url("https://initial.example.test/api/v1/auth/token")
            .build()

        assertEquals(
            "https://first.example.test/api/v1/auth/token",
            intercept(clients.publicHttpClient, request).url.toString(),
        )

        currentHost = "https://second.example.test:8443/"

        assertEquals(
            "https://second.example.test:8443/api/v1/auth/token",
            intercept(clients.publicHttpClient, request).url.toString(),
        )
    }

    @Test
    fun protectedClientReadsDynamicOriginAndKeepsPathBearerAndNoCookieBoundary() =
        kotlinx.coroutines.runBlocking {
            val state = SessionState()
            SessionManager(MemoryStore(), state).install(tokenBundle())
            var currentHost = "https://first.example.test/"
            val clients = ApiClients(
                baseUrl = "https://initial.example.test/",
                sessionState = state,
                hostProvider = { currentHost },
            )
            val request = Request.Builder()
                .url("https://initial.example.test/api/v1/points/ledger?page=2")
                .header("Authorization", "Basic leaked")
                .header("Cookie", "pq_refresh=leaked")
                .header("X-CSRF-Token", "leaked")
                .build()

            val first = intercept(clients.protectedHttpClient, request)

            assertEquals(
                "https://first.example.test/api/v1/points/ledger?page=2",
                first.url.toString(),
            )
            assertEquals("Bearer access-secret", first.header("Authorization"))
            assertNull(first.header("Cookie"))
            assertNull(first.header("X-CSRF-Token"))
            assertSame(okhttp3.CookieJar.NO_COOKIES, clients.protectedHttpClient.cookieJar)

            currentHost = "https://second.example.test:8443/"

            val second = intercept(clients.protectedHttpClient, request)
            assertEquals(
                "https://second.example.test:8443/api/v1/points/ledger?page=2",
                second.url.toString(),
            )
            assertEquals("Bearer access-secret", second.header("Authorization"))
            assertNull(second.header("Cookie"))
        }

    @Test
    fun bothBuildersUseExactTimeoutsNoCookiesAndNoLogging() {
        val state = SessionState()
        val clients = listOf(
            ApiClients.publicBuilder().build(),
            ApiClients.protectedBuilder(state).build(),
        )

        clients.forEach { client ->
            assertEquals(10_000, client.connectTimeoutMillis)
            assertEquals(20_000, client.readTimeoutMillis)
            assertEquals(20_000, client.writeTimeoutMillis)
            assertEquals(30_000, client.callTimeoutMillis)
            assertSame(okhttp3.CookieJar.NO_COOKIES, client.cookieJar)
            assertFalse(client.interceptors.any { it is HttpLoggingInterceptor })
            assertFalse(client.networkInterceptors.any { it is HttpLoggingInterceptor })
        }
    }

    @Test
    fun publicClientRemovesAllAuthenticationHeaders() {
        val interceptor = ApiClients.publicBuilder().build().interceptors.single()
        val chain = RecordingChain(sensitiveRequest())

        interceptor.intercept(chain)

        assertNull(chain.proceededRequest?.header("Authorization"))
        assertNull(chain.proceededRequest?.header("Cookie"))
        assertNull(chain.proceededRequest?.header("X-CSRF-Token"))
    }

    @Test
    fun protectedClientSendsOnlyCurrentBearerAuthentication() = kotlinx.coroutines.runBlocking {
        val state = SessionState()
        val manager = SessionManager(MemoryStore(), state)
        manager.install(tokenBundle())
        val interceptor = ApiClients.protectedBuilder(state).build().interceptors.single()
        val chain = RecordingChain(sensitiveRequest())

        interceptor.intercept(chain)

        assertEquals("Bearer access-secret", chain.proceededRequest?.header("Authorization"))
        assertNull(chain.proceededRequest?.header("Cookie"))
        assertNull(chain.proceededRequest?.header("X-CSRF-Token"))
    }

    @Test
    fun protectedClientWithoutSessionRemovesPreexistingAuthentication() {
        val interceptor = ApiClients.protectedBuilder(SessionState()).build().interceptors.single()
        val chain = RecordingChain(sensitiveRequest())

        interceptor.intercept(chain)

        assertNull(chain.proceededRequest?.header("Authorization"))
        assertNull(chain.proceededRequest?.header("Cookie"))
        assertNull(chain.proceededRequest?.header("X-CSRF-Token"))
    }

    private fun sensitiveRequest() = Request.Builder()
        .url("https://example.test/api/v1/auth/token")
        .header("Authorization", "Basic leaked")
        .header("Cookie", "pq_refresh=leaked")
        .header("X-CSRF-Token", "leaked")
        .build()

    private fun intercept(client: OkHttpClient, request: Request): Request =
        client.interceptors.fold(request) { currentRequest, interceptor ->
            RecordingChain(currentRequest).also(interceptor::intercept).proceededRequest!!
        }

    private class MemoryStore : SessionStore {
        private var value: StoredRefreshSession? = null
        override suspend fun read() = value
        override suspend fun write(value: StoredRefreshSession) {
            this.value = value
        }
        override suspend fun clear() {
            value = null
        }
    }

    private class RecordingChain(
        private val initialRequest: Request,
    ) : Interceptor.Chain {
        var proceededRequest: Request? = null

        override fun request(): Request = initialRequest
        override fun proceed(request: Request): Response {
            proceededRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody())
                .build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = OkHttpClient().newCall(initialRequest)
        override fun connectTimeoutMillis(): Int = 10_000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 20_000
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 20_000
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private companion object {
        fun tokenBundle() = TokenBundle(
            accessToken = "access-secret",
            accessTokenExpiresAt = Instant.parse("2030-01-01T01:00:00Z"),
            refreshToken = "refresh-secret",
            refreshTokenExpiresAt = Instant.parse("2030-02-01T00:00:00Z"),
            user = User("student-1", "student", UserRole.STUDENT, 42),
        )
    }
}
