package com.pointquest.android.feature.shop

import com.pointquest.android.core.model.Product
import com.pointquest.android.core.ui.UiText

data class ProductDetailUiState(
    val product: Product? = null,
    val balance: Int? = null,
    val loading: Boolean = true,
    val error: UiText? = null,
    val message: UiText? = null,
    val showRedeemConfirmation: Boolean = false,
    val redeeming: Boolean = false,
    val redeemRetryPending: Boolean = false,
) {
    val canRedeem: Boolean
        get() = product?.let { item ->
            item.isActive && item.stock > 0 && balance != null && balance >= item.pointsCost && !redeeming
        } == true

    val pointsDeficit: Int?
        get() {
            val item = product ?: return null
            val current = balance ?: return null
            return (item.pointsCost - current).takeIf { it > 0 }
        }

    val remainingBalanceAfterRedeem: Int?
        get() {
            val item = product ?: return null
            val current = balance ?: return null
            return (current - item.pointsCost).coerceAtLeast(0)
        }
}

sealed interface ProductDetailEvent {
    data class NavigateToOrder(val orderId: String) : ProductDetailEvent
    data object ReturnToShop : ProductDetailEvent
}
