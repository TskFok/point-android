package com.pointquest.android.core.model

import java.time.Instant

data class Order(
    val id: String,
    val orderNo: String,
    val userId: String,
    val productId: String,
    val productNameSnapshot: String,
    val productImageKeySnapshot: String,
    val pointsCostSnapshot: Int,
    val status: OrderStatus,
    val balance: Int,
    val createdAt: Instant,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val updatedBy: String?,
)

enum class OrderStatus {
    PENDING_PICKUP,
    COMPLETED,
    CANCELLED,
    UNKNOWN,
}
