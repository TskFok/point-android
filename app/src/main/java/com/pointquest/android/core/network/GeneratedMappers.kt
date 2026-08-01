package com.pointquest.android.core.network

import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.OrderStatus
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PointLedgerType
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.QuestionOption
import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.generated.model.AnswerResultDto
import com.pointquest.android.generated.model.AuthRefresh201Response
import com.pointquest.android.generated.model.LearnerQuestionDto
import com.pointquest.android.generated.model.OrderDto
import com.pointquest.android.generated.model.PageMetaDto
import com.pointquest.android.generated.model.PointLedgerDto
import com.pointquest.android.generated.model.ProductDto
import com.pointquest.android.generated.model.PublicUserDto
import com.pointquest.android.generated.model.TokenResponseDto
import java.time.Instant

fun TokenResponseDto.toDomain(now: Instant): TokenBundle = TokenBundle(
    accessToken = accessToken,
    accessTokenExpiresAt = now.plusSeconds(accessTokenExpiresIn.toLong()),
    refreshToken = refreshToken,
    refreshTokenExpiresAt = refreshTokenExpiresAt.toInstant(),
    user = user.toDomain(),
)

fun AuthRefresh201Response.toDomain(now: Instant): TokenBundle = TokenBundle(
    accessToken = accessToken,
    accessTokenExpiresAt = now.plusSeconds(accessTokenExpiresIn.toLong()),
    refreshToken = refreshToken,
    refreshTokenExpiresAt = refreshTokenExpiresAt.toInstant(),
    user = user.toDomain(),
)

fun LearnerQuestionDto.toDomain(): Question = Question(
    id = id,
    stem = stem,
    basePoints = basePoints,
    options = options.sortedBy { it.position }.map { option ->
        QuestionOption(
            id = option.id,
            label = option.label,
            content = option.content,
            position = option.position,
        )
    },
)

fun AnswerResultDto.toDomain(): AnswerResult = AnswerResult(
    balance = balance,
    correct = correct,
    correctOptionId = correctOptionId,
    errorCount = errorCount,
    explanation = explanation,
    pointsAwarded = pointsAwarded,
    selectedOptionId = selectedOptionId,
)

fun ProductDto.toDomain(): Product = Product(
    id = id,
    name = name,
    description = description,
    imageKey = imageKey,
    pointsCost = pointsCost,
    stock = stock,
    isActive = isActive,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

fun OrderDto.toDomain(): Order = Order(
    id = id,
    orderNo = orderNo,
    userId = userId,
    productId = productId,
    productNameSnapshot = productNameSnapshot,
    productImageKeySnapshot = productImageKeySnapshot,
    pointsCostSnapshot = pointsCostSnapshot,
    status = status.toDomain(),
    balance = balance,
    createdAt = createdAt.toInstant(),
    completedAt = completedAt?.toInstant(),
    cancelledAt = cancelledAt?.toInstant(),
    updatedBy = updatedBy,
)

fun PointLedgerDto.toDomain(): PointLedgerEntry = PointLedgerEntry(
    id = id,
    userId = userId,
    type = type.toDomain(),
    delta = delta,
    balanceAfter = balanceAfter,
    answerAttemptId = answerAttemptId,
    orderId = orderId,
    createdAt = createdAt.toInstant(),
)

fun PageMetaDto.toDomain(): PageMeta = PageMeta(
    page = page,
    pageSize = pageSize,
    total = total,
    totalPages = totalPages,
)

fun PublicUserDto.toDomain(): User = User(
    id = id,
    username = username,
    role = role.toDomain(),
    pointsBalance = pointsBalance,
)

private fun PublicUserDto.Role.toDomain(): UserRole = when (value) {
    "ADMIN" -> UserRole.ADMIN
    "STUDENT" -> UserRole.STUDENT
    else -> UserRole.UNKNOWN
}

private fun OrderDto.Status.toDomain(): OrderStatus = when (value) {
    "PENDING_PICKUP" -> OrderStatus.PENDING_PICKUP
    "COMPLETED" -> OrderStatus.COMPLETED
    "CANCELLED" -> OrderStatus.CANCELLED
    else -> OrderStatus.UNKNOWN
}

private fun PointLedgerDto.Type.toDomain(): PointLedgerType = when (value) {
    "ANSWER_REWARD" -> PointLedgerType.ANSWER_REWARD
    "ORDER_REDEEM" -> PointLedgerType.ORDER_REDEEM
    "ORDER_REFUND" -> PointLedgerType.ORDER_REFUND
    else -> PointLedgerType.UNKNOWN
}
