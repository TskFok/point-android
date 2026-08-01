package com.pointquest.android.data.products

import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.network.AppResult

interface ProductsRepository {
    suspend fun page(search: String?, page: Int): AppResult<Page<Product>>
    suspend fun detail(id: String): AppResult<Product>
}
