package com.pointquest.android.core.model

import java.time.Instant

data class PointLedgerEntry(
    val id: String,
    val userId: String,
    val type: PointLedgerType,
    val delta: Int,
    val balanceAfter: Int,
    val answerAttemptId: String?,
    val orderId: String?,
    val createdAt: Instant,
)

enum class PointLedgerType {
    ANSWER_REWARD,
    ORDER_REDEEM,
    ORDER_REFUND,
    UNKNOWN,
}
