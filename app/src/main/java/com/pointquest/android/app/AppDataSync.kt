package com.pointquest.android.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-local synchronization for server-owned values changed by successful user operations. */
class AppDataSync {
    private val mutableBalance = MutableStateFlow<Int?>(null)
    private val mutableHomeRefreshRevision = MutableStateFlow(0L)
    private val mutableShopRefreshRevision = MutableStateFlow(0L)
    private val mutableInactiveProductIds = MutableStateFlow<Set<String>>(emptySet())

    val balance: StateFlow<Int?> = mutableBalance.asStateFlow()
    val homeRefreshRevision: StateFlow<Long> = mutableHomeRefreshRevision.asStateFlow()
    val shopRefreshRevision: StateFlow<Long> = mutableShopRefreshRevision.asStateFlow()
    val inactiveProductIds: StateFlow<Set<String>> = mutableInactiveProductIds.asStateFlow()

    fun recordPracticeChanged(balance: Int? = null) {
        publishBalance(balance)
        mutableHomeRefreshRevision.update(Long::inc)
    }

    fun recordOrderCreated(balance: Int) {
        publishBalance(balance)
        mutableHomeRefreshRevision.update(Long::inc)
        mutableShopRefreshRevision.update(Long::inc)
    }

    fun recordProductInactive(productId: String) {
        mutableInactiveProductIds.update { it + productId }
        mutableShopRefreshRevision.update(Long::inc)
    }

    private fun publishBalance(balance: Int?) {
        if (balance != null && balance >= 0) mutableBalance.value = balance
    }
}
