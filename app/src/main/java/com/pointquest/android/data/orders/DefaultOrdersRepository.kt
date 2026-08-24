package com.pointquest.android.data.orders

import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.AuthorizedCallExecutor
import com.pointquest.android.core.network.RetryExecutor
import com.pointquest.android.data.gateway.StudentGateway

class DefaultOrdersRepository(
    private val gateway: StudentGateway,
    private val authorized: AuthorizedCallExecutor,
    private val retry: RetryExecutor,
) : OrdersRepository {
    override suspend fun redeem(productId: String, idempotencyKey: String?): AppResult<Order> =
        authorized.executeOperation {
            retry.executeIdempotent(RedeemPayload(productId), key = idempotencyKey) { frozen ->
                execute { gateway.createOrder(frozen.payload.productId, frozen.key) }
            }
        }

    override suspend fun page(page: Int): AppResult<Page<Order>> =
        read { gateway.orders(page, PAGE_SIZE) }

    override suspend fun detail(id: String): AppResult<Order> = read { gateway.order(id) }

    private suspend fun <T> read(operation: suspend () -> AppResult<T>): AppResult<T> =
        authorized.executeOperation { retry.executeRead { execute(operation) } }

    private data class RedeemPayload(val productId: String)

    private companion object {
        const val PAGE_SIZE = 20
    }
}
