package com.pointquest.android.data.products

import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.AuthorizedCallExecutor
import com.pointquest.android.core.network.RetryExecutor
import com.pointquest.android.data.gateway.StudentGateway

class DefaultProductsRepository(
    private val gateway: StudentGateway,
    private val authorized: AuthorizedCallExecutor,
    private val retry: RetryExecutor,
) : ProductsRepository {
    override suspend fun page(search: String?, page: Int): AppResult<Page<Product>> =
        read { gateway.products(search?.takeUnless(String::isBlank), page, PAGE_SIZE) }

    override suspend fun detail(id: String): AppResult<Product> = read { gateway.product(id) }

    private suspend fun <T> read(operation: suspend () -> AppResult<T>): AppResult<T> =
        retry.executeRead { authorized.execute(operation) }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
