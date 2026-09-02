package com.pointquest.android.feature.shop

import com.pointquest.android.core.model.Product
import com.pointquest.android.core.network.PagedState
import com.pointquest.android.core.ui.UiText

data class ProductListUiState(
    val search: String = "",
    val paged: PagedState<Product> = PagedState(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
    val refreshError: UiText? = null,
    val loadMoreError: UiText? = null,
    val balance: Int? = null,
    val balanceFailed: Boolean = false,
) {
    val items: List<Product>
        get() = paged.items

    val canLoadMore: Boolean
        get() = paged.canLoadMore && !loading && !refreshing && !loadingMore

    val empty: Boolean
        get() = !loading && error == null && items.isEmpty()

    fun pointsDeficit(product: Product): Int? {
        val current = balance ?: return null
        if (!product.isActive || product.stock <= 0) return null
        return (product.pointsCost - current).takeIf { it > 0 }
    }
}
