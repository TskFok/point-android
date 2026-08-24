package com.pointquest.android.data.orders

import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.network.AppResult

interface OrdersRepository {
    suspend fun redeem(productId: String, idempotencyKey: String? = null): AppResult<Order>
    suspend fun page(page: Int): AppResult<Page<Order>>
    suspend fun detail(id: String): AppResult<Order>
}
