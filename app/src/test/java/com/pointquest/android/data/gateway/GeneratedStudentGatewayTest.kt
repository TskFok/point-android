package com.pointquest.android.data.gateway

import com.pointquest.android.core.auth.SessionManager
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStore
import com.pointquest.android.core.auth.StoredRefreshSession
import com.pointquest.android.core.model.PointLedgerType
import com.pointquest.android.core.model.OrderStatus
import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.ApiClients
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.generated.api.DefaultApi
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GeneratedStudentGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: GeneratedStudentGateway

    @Before
    fun setUp() = runBlocking {
        server = MockWebServer()
        server.start()
        val state = SessionState()
        SessionManager(MemoryStore(), state).install(
            TokenBundle(
                "access-secret", Instant.parse("2030-01-01T00:05:00Z"),
                "refresh-secret", Instant.parse("2030-02-01T00:00:00Z"),
                User("u1", "student", UserRole.STUDENT, 42),
            ),
        )
        val api = ApiClients.defaultApi(server.url("/").toString(), ApiClients.protectedBuilder(state))
        gateway = GeneratedStudentGateway(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun practiceReadsUseGeneratedPathsQueryAndCompleteMappings() = runBlocking {
        enqueue(summaryJson)
        enqueue(questionJson)
        enqueue(wrongQuestionsJson)

        val summary = (gateway.practiceSummary() as AppResult.Success).value
        val question = (gateway.randomQuestion(listOf("q 1", "q2")) as AppResult.Success).value
        val wrong = (gateway.wrongQuestions(3, 20) as AppResult.Success).value

        assertEquals(listOf(10, 42, 2, 1, 3, 5), listOf(
            summary.activeTotal, summary.balance, summary.firstAnsweredCount,
            summary.masteredWrongCount, summary.pendingWrongCount, summary.unansweredCount,
        ))
        assertEquals(listOf("o1", "o2"), question.options.map { it.id })
        assertEquals(2, wrong.items.single().errorCount)
        assertEquals(Instant.parse("2030-01-02T03:04:05Z"), wrong.items.single().firstAnsweredAt)
        assertEquals(3, wrong.meta.page)
        assertRequest("/api/v1/practice/summary")
        assertRequest("/api/v1/practice/random?excludeIds=q%201%2Cq2")
        assertRequest("/api/v1/practice/wrong-questions?page=3&pageSize=20")
    }

    @Test
    fun answerWritesUseBodyFrozenKeyAndAndroidHeadersOnly() = runBlocking {
        enqueue(answerJson)
        enqueue(answerJson)

        gateway.answerFirst("q/1", "o2", "key-first")
        gateway.answerWrong("q/1", "o3", "key-wrong")

        val first = requireNotNull(server.takeRequest())
        val wrong = requireNotNull(server.takeRequest())
        assertEquals("/api/v1/practice/questions/q%2F1/answer", first.path)
        assertEquals("key-first", first.getHeader("Idempotency-Key"))
        assertEquals("{\"selectedOptionId\":\"o2\"}", first.body.readUtf8())
        assertEquals("/api/v1/practice/wrong-questions/q%2F1/answer", wrong.path)
        assertEquals("key-wrong", wrong.getHeader("Idempotency-Key"))
        assertEquals("{\"selectedOptionId\":\"o3\"}", wrong.body.readUtf8())
        listOf(first, wrong).forEach { request ->
            assertEquals("Bearer access-secret", request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            assertNull(request.getHeader("X-CSRF-Token"))
        }
    }

    @Test
    fun pointsMapBalanceAndEveryLedgerField() = runBlocking {
        enqueue("""{"balance":42}""")
        enqueue(ledgerJson)

        val balance = (gateway.pointBalance() as AppResult.Success).value
        val page = (gateway.pointLedger(2, 20) as AppResult.Success).value
        val entry = page.items.single()

        assertEquals(42, balance)
        assertEquals("l1", entry.id)
        assertEquals("u1", entry.userId)
        assertEquals(PointLedgerType.ANSWER_REWARD, entry.type)
        assertEquals(5, entry.delta)
        assertEquals(42, entry.balanceAfter)
        assertEquals("a1", entry.answerAttemptId)
        assertEquals(null, entry.orderId)
        assertEquals(Instant.parse("2030-01-02T03:04:05Z"), entry.createdAt)
        assertEquals(2, page.meta.page)
        assertRequest("/api/v1/points/balance")
        assertRequest("/api/v1/points/ledger?page=2&pageSize=20")
    }

    @Test
    fun productAndOrderOperationsUseAllFiveGeneratedContracts() = runBlocking {
        enqueue(productPageJson)
        enqueue(productJson)
        enqueue(orderJson)
        enqueue(orderPageJson)
        enqueue(orderJson)

        gateway.products("pencil box", 2, 20)
        gateway.product("p/1")
        gateway.createOrder("p1", "order-key")
        gateway.orders(3, 20)
        gateway.order("ord/1")

        assertRequest("/api/v1/products?search=pencil%20box&isActive=true&page=2&pageSize=20")
        assertRequest("/api/v1/products/p%2F1")
        val create = requireNotNull(server.takeRequest())
        assertEquals("/api/v1/orders", create.path)
        assertEquals("order-key", create.getHeader("Idempotency-Key"))
        assertEquals("{\"productId\":\"p1\"}", create.body.readUtf8())
        assertEquals("Bearer access-secret", create.getHeader("Authorization"))
        assertNull(create.getHeader("Cookie"))
        assertNull(create.getHeader("X-CSRF-Token"))
        assertRequest("/api/v1/orders?page=3&pageSize=20")
        assertRequest("/api/v1/orders/ord%2F1")
    }

    @Test
    fun unknownGeneratedEnumValueDeserializesAndMapsToDomainFallback() = runBlocking {
        enqueue(
            orderPageJson.replace(
                "\"status\":\"PENDING_PICKUP\"",
                "\"status\":\"RETURNED_BY_FUTURE_SERVER\"",
            ),
        )

        val result = gateway.orders(1, 20)

        assertTrue(result is AppResult.Success)
        assertEquals(OrderStatus.UNKNOWN, (result as AppResult.Success).value.items.single().status)
    }

    @Test
    fun stableApiErrorAndMalformedOrIoResponsesKeepExistingBoundaries() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody(
            """{"code":"QUESTION_ALREADY_ANSWERED","message":"done","requestId":"r1"}""",
        ))
        val conflict = gateway.answerFirst("q1", "o2", "key") as AppResult.Failure
        assertEquals("QUESTION_ALREADY_ANSWERED", conflict.error.code)
        assertEquals("r1", conflict.error.requestId)

        enqueue("""{"activeTotal":10}""")
        val invalid = gateway.practiceSummary() as AppResult.Failure
        assertEquals("INVALID_RESPONSE", invalid.error.code)

        server.shutdown()
        val io = gateway.pointBalance() as AppResult.Failure
        assertEquals("NETWORK_ERROR", io.error.code)
        assertTrue(io.error.cause is IOException)
    }

    @Test
    fun cancellationIsRethrown() = runBlocking {
        val cancellation = CancellationException("cancelled")
        try {
            GeneratedStudentGateway(cancellingApi(cancellation)).pointBalance()
            fail("CancellationException should be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    private fun assertRequest(expectedPath: String) {
        val request = requireNotNull(server.takeRequest())
        assertEquals(expectedPath, request.path)
        assertEquals("Bearer access-secret", request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie"))
        assertNull(request.getHeader("X-CSRF-Token"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun cancellingApi(cancellation: CancellationException): DefaultApi =
        Proxy.newProxyInstance(DefaultApi::class.java.classLoader, arrayOf(DefaultApi::class.java)) { _, _, args ->
            val continuation = requireNotNull(args).last() as Continuation<Any?>
            continuation.resumeWith(Result.failure(cancellation))
            COROUTINE_SUSPENDED
        } as DefaultApi

    private class MemoryStore : SessionStore {
        private var value: StoredRefreshSession? = null
        override suspend fun read() = value
        override suspend fun write(value: StoredRefreshSession) { this.value = value }
        override suspend fun clear() { value = null }
    }

    private companion object {
        val summaryJson = """{"activeTotal":10,"balance":42,"firstAnsweredCount":2,"masteredWrongCount":1,"pendingWrongCount":3,"unansweredCount":5}"""
        val questionJson = """{"basePoints":5,"id":"q1","options":[{"content":"2","id":"o2","label":"B","position":2},{"content":"1","id":"o1","label":"A","position":1}],"stem":"1+1?"}"""
        val answerJson = """{"balance":47,"correct":true,"correctOptionId":"o2","errorCount":0,"explanation":"yes","pointsAwarded":5,"selectedOptionId":"o2"}"""
        val wrongQuestionsJson = """{"data":[{"errorCount":2,"firstAnsweredAt":"2030-01-02T03:04:05Z","masteredAt":null,"question":$questionJson}],"meta":{"page":3,"pageSize":20,"total":1,"totalPages":1}}"""
        val ledgerJson = """{"data":[{"answerAttemptId":"a1","balanceAfter":42,"createdAt":"2030-01-02T03:04:05Z","delta":5,"id":"l1","orderId":null,"type":"ANSWER_REWARD","userId":"u1"}],"meta":{"page":2,"pageSize":20,"total":1,"totalPages":1}}"""
        val productJson = """{"createdAt":"2030-01-01T00:00:00Z","description":"desc","id":"p1","imageKey":"products/p1.png","isActive":true,"name":"Pencil","pointsCost":10,"stock":2,"updatedAt":"2030-01-02T00:00:00Z"}"""
        val productPageJson = """{"data":[$productJson],"meta":{"page":2,"pageSize":20,"total":1,"totalPages":1}}"""
        val orderJson = """{"balance":32,"cancelledAt":null,"completedAt":null,"createdAt":"2030-01-02T00:00:00Z","id":"ord1","orderNo":"N1","pointsCostSnapshot":10,"productId":"p1","productImageKeySnapshot":"products/p1.png","productNameSnapshot":"Pencil","status":"PENDING_PICKUP","updatedBy":null,"userId":"u1"}"""
        val orderPageJson = """{"data":[$orderJson],"meta":{"page":3,"pageSize":20,"total":1,"totalPages":1}}"""
    }
}
