package com.pointquest.android.core.network

import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.OrderStatus
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PointLedgerType
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.QuestionOption
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.generated.model.AnswerResultDto
import com.pointquest.android.generated.model.AuthRefresh201Response
import com.pointquest.android.generated.model.LearnerQuestionDto
import com.pointquest.android.generated.model.LearnerQuestionOptionDto
import com.pointquest.android.generated.model.OrderDto
import com.pointquest.android.generated.model.PageMetaDto
import com.pointquest.android.generated.model.PointLedgerDto
import com.pointquest.android.generated.model.ProductDto
import com.pointquest.android.generated.model.PublicUserDto
import com.pointquest.android.generated.model.TokenResponseDto
import java.time.Instant
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneratedMappersTest {
    private val user = PublicUserDto(
        id = "user_1",
        pointsBalance = 12,
        role = PublicUserDto.Role.STUDENT,
        username = "learner",
    )

    @Test
    fun mapsTokenResponseWithExpiryRelativeToProvidedClock() {
        val now = Instant.parse("2026-08-01T00:00:00Z")
        val dto = TokenResponseDto(
            accessToken = "access",
            accessTokenExpiresIn = 3600,
            refreshToken = "refresh",
            refreshTokenExpiresAt = OffsetDateTime.parse("2026-08-02T00:00:00Z"),
            user = user,
        )

        val result = dto.toDomain(now)

        assertEquals("access", result.accessToken)
        assertEquals(Instant.parse("2026-08-01T01:00:00Z"), result.accessTokenExpiresAt)
        assertEquals("refresh", result.refreshToken)
        assertEquals(Instant.parse("2026-08-02T00:00:00Z"), result.refreshTokenExpiresAt)
        assertEquals("user_1", result.user.id)
    }

    @Test
    fun mapsRefreshResponseWithExpiryRelativeToProvidedClock() {
        val now = Instant.parse("2026-08-01T00:00:00Z")
        val dto = AuthRefresh201Response(
            user = user,
            accessToken = "new-access",
            accessTokenExpiresIn = 120,
            refreshToken = "new-refresh",
            refreshTokenExpiresAt = OffsetDateTime.parse("2026-08-03T00:00:00Z"),
        )

        val result = dto.toDomain(now)

        assertEquals("new-access", result.accessToken)
        assertEquals(Instant.parse("2026-08-01T00:02:00Z"), result.accessTokenExpiresAt)
        assertEquals("new-refresh", result.refreshToken)
        assertEquals(Instant.parse("2026-08-03T00:00:00Z"), result.refreshTokenExpiresAt)
    }

    @Test
    fun sortsQuestionOptionsByTheirServerPosition() {
        val dto = LearnerQuestionDto(
            basePoints = 3,
            id = "question_1",
            options = listOf(
                LearnerQuestionOptionDto("内容 C", "option_c", "C", 3),
                LearnerQuestionOptionDto("内容 A", "option_a", "A", 1),
                LearnerQuestionOptionDto("内容 B", "option_b", "B", 2),
            ),
            stem = "题干",
        )

        val result = dto.toDomain()

        assertEquals(
            Question(
                id = "question_1",
                stem = "题干",
                basePoints = 3,
                options = listOf(
                    QuestionOption("option_a", "A", "内容 A", 1),
                    QuestionOption("option_b", "B", "内容 B", 2),
                    QuestionOption("option_c", "C", "内容 C", 3),
                ),
            ),
            result,
        )
    }

    @Test
    fun mapsAnswerResultFields() {
        val answer = AnswerResultDto(
            balance = 9,
            correct = false,
            correctOptionId = "option_correct",
            errorCount = 2,
            explanation = "解析",
            pointsAwarded = 0,
            selectedOptionId = "option_selected",
        ).toDomain()

        assertEquals(9, answer.balance)
        assertEquals(false, answer.correct)
        assertEquals("option_correct", answer.correctOptionId)
        assertEquals(2, answer.errorCount)
        assertEquals("解析", answer.explanation)
        assertEquals(0, answer.pointsAwarded)
        assertEquals("option_selected", answer.selectedOptionId)
    }

    @Test
    fun mapsProductFields() {
        val product = ProductDto(
            createdAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
            description = "商品描述",
            id = "product_1",
            imageKey = "product.png",
            isActive = true,
            name = "商品",
            pointsCost = 8,
            stock = 5,
            updatedAt = OffsetDateTime.parse("2026-07-02T00:00:00Z"),
        ).toDomain()

        assertEquals("product_1", product.id)
        assertEquals("商品", product.name)
        assertEquals("商品描述", product.description)
        assertEquals("product.png", product.imageKey)
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), product.createdAt)
        assertEquals(8, product.pointsCost)
        assertEquals(5, product.stock)
        assertEquals(true, product.isActive)
        assertEquals(Instant.parse("2026-07-02T00:00:00Z"), product.updatedAt)
    }

    @Test
    fun mapsEveryOrderStatus() {
        val expected = mapOf(
            OrderDto.Status.PENDING_PICKUP to OrderStatus.PENDING_PICKUP,
            OrderDto.Status.COMPLETED to OrderStatus.COMPLETED,
            OrderDto.Status.CANCELLED to OrderStatus.CANCELLED,
        )

        expected.forEach { (source, target) ->
            assertEquals(target, order(source).toDomain().status)
        }
    }

    @Test
    fun mapsEveryOrderField() {
        val result = OrderDto(
            balance = 4,
            cancelledAt = OffsetDateTime.parse("2026-08-01T03:00:00+03:00"),
            completedAt = OffsetDateTime.parse("2026-08-01T02:00:00+02:00"),
            createdAt = OffsetDateTime.parse("2026-08-01T00:00:00Z"),
            id = "order_1",
            orderNo = "O-1",
            pointsCostSnapshot = 8,
            productId = "product_1",
            productImageKeySnapshot = "product.png",
            productNameSnapshot = "商品",
            status = OrderDto.Status.COMPLETED,
            updatedBy = "admin_1",
            userId = "user_1",
        ).toDomain()

        assertEquals(
            Order(
                id = "order_1",
                orderNo = "O-1",
                userId = "user_1",
                productId = "product_1",
                productNameSnapshot = "商品",
                productImageKeySnapshot = "product.png",
                pointsCostSnapshot = 8,
                status = OrderStatus.COMPLETED,
                balance = 4,
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
                completedAt = Instant.parse("2026-08-01T00:00:00Z"),
                cancelledAt = Instant.parse("2026-08-01T00:00:00Z"),
                updatedBy = "admin_1",
            ),
            result,
        )
    }

    @Test
    fun preservesNullableOrderFields() {
        val result = order(OrderDto.Status.PENDING_PICKUP).toDomain()

        assertNull(result.completedAt)
        assertNull(result.cancelledAt)
        assertNull(result.updatedBy)
    }

    @Test
    fun mapsEveryPointLedgerType() {
        val expected = mapOf(
            PointLedgerDto.Type.ANSWER_REWARD to PointLedgerType.ANSWER_REWARD,
            PointLedgerDto.Type.ORDER_REDEEM to PointLedgerType.ORDER_REDEEM,
            PointLedgerDto.Type.ORDER_REFUND to PointLedgerType.ORDER_REFUND,
        )

        expected.forEach { (source, target) ->
            assertEquals(target, ledger(source).toDomain().type)
        }
    }

    @Test
    fun mapsEveryPointLedgerField() {
        val result = PointLedgerDto(
            answerAttemptId = "attempt_1",
            balanceAfter = 5,
            createdAt = OffsetDateTime.parse("2026-08-01T08:00:00+08:00"),
            delta = 2,
            id = "ledger_1",
            orderId = "order_1",
            type = PointLedgerDto.Type.ANSWER_REWARD,
            userId = "user_1",
        ).toDomain()

        assertEquals(
            PointLedgerEntry(
                id = "ledger_1",
                userId = "user_1",
                type = PointLedgerType.ANSWER_REWARD,
                delta = 2,
                balanceAfter = 5,
                answerAttemptId = "attempt_1",
                orderId = "order_1",
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
            ),
            result,
        )
    }

    @Test
    fun preservesNullablePointLedgerAssociationIds() {
        val result = ledger(PointLedgerDto.Type.ORDER_REFUND).toDomain()

        assertNull(result.answerAttemptId)
        assertNull(result.orderId)
    }

    @Test
    fun mapsUnknownWireEnumValuesToUnknownDomainValues() {
        assertEquals(UserRole.UNKNOWN, userRoleFromWire("SUSPENDED"))
        assertEquals(OrderStatus.UNKNOWN, orderStatusFromWire("RETURNED"))
        assertEquals(PointLedgerType.UNKNOWN, pointLedgerTypeFromWire("MANUAL_ADJUSTMENT"))
    }

    @Test
    fun mapsEveryPageMetaField() {
        val result = PageMetaDto(page = 2, pageSize = 20, total = 47, totalPages = 3).toDomain()

        assertEquals(2, result.page)
        assertEquals(20, result.pageSize)
        assertEquals(47, result.total)
        assertEquals(3, result.totalPages)
    }

    private fun order(status: OrderDto.Status) = OrderDto(
        balance = 4,
        cancelledAt = null,
        completedAt = null,
        createdAt = OffsetDateTime.parse("2026-08-01T00:00:00Z"),
        id = "order_1",
        orderNo = "O-1",
        pointsCostSnapshot = 8,
        productId = "product_1",
        productImageKeySnapshot = "product.png",
        productNameSnapshot = "商品",
        status = status,
        updatedBy = null,
        userId = "user_1",
    )

    private fun ledger(type: PointLedgerDto.Type) = PointLedgerDto(
        answerAttemptId = null,
        balanceAfter = 5,
        createdAt = OffsetDateTime.parse("2026-08-01T00:00:00Z"),
        delta = 2,
        id = "ledger_1",
        orderId = null,
        type = type,
        userId = "user_1",
    )
}
