package com.pointquest.android.app

import com.pointquest.android.core.auth.ActiveSession
import com.pointquest.android.core.auth.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppDataSession(
    val userId: String,
    val generation: Long,
)

class AppDataSyncState internal constructor(
    val session: AppDataSession?,
    val balance: Int? = null,
    val homeRefreshRevision: Long = 0L,
    val shopRefreshRevision: Long = 0L,
    internal val inactiveProductRevisions: Map<String, Long> = emptyMap(),
) {
    val inactiveProductIds: Set<String>
        get() = inactiveProductRevisions.keys
}

class ShopRefreshSnapshot internal constructor(
    internal val session: AppDataSession,
    internal val revision: Long,
)

/** Process-local synchronization scoped to the current authenticated session generation. */
class AppDataSync(sessionState: SessionState) {
    private val mutableState = MutableStateFlow(AppDataSyncState(session = null))
    val state: StateFlow<AppDataSyncState> = mutableState.asStateFlow()

    init {
        sessionState.observeActiveSession { activeSession ->
            mutableState.value = AppDataSyncState(session = activeSession?.toAppDataSession())
        }
    }

    fun captureSession(): AppDataSession? = mutableState.value.session

    fun captureShopRefresh(): ShopRefreshSnapshot? = mutableState.value.let { current ->
        current.session?.let { ShopRefreshSnapshot(it, current.shopRefreshRevision) }
    }

    fun recordPracticeChanged(session: AppDataSession, balance: Int? = null) {
        mutableState.update { current ->
            if (current.session != session) return@update current
            current.copy(
                balance = validBalance(balance) ?: current.balance,
                homeRefreshRevision = current.homeRefreshRevision + 1,
            )
        }
    }

    fun recordOrderCreated(session: AppDataSession, balance: Int) {
        mutableState.update { current ->
            if (current.session != session) return@update current
            current.copy(
                balance = validBalance(balance) ?: current.balance,
                homeRefreshRevision = current.homeRefreshRevision + 1,
                shopRefreshRevision = current.shopRefreshRevision + 1,
            )
        }
    }

    fun recordProductInactive(session: AppDataSession, productId: String) {
        mutableState.update { current ->
            if (current.session != session) return@update current
            val nextRevision = current.shopRefreshRevision + 1
            current.copy(
                shopRefreshRevision = nextRevision,
                inactiveProductRevisions = current.inactiveProductRevisions + (productId to nextRevision),
            )
        }
    }

    fun inactiveProductIdsNotCoveredBy(snapshot: ShopRefreshSnapshot): Set<String> =
        mutableState.value.let { current ->
            if (current.session != snapshot.session) {
                current.inactiveProductIds
            } else {
                current.inactiveProductRevisions
                    .filterValues { it > snapshot.revision }
                    .keys
            }
        }

    fun acknowledgeShopRefresh(snapshot: ShopRefreshSnapshot) {
        mutableState.update { current ->
            if (current.session != snapshot.session) return@update current
            current.copy(
                inactiveProductRevisions = current.inactiveProductRevisions
                    .filterValues { it > snapshot.revision },
            )
        }
    }

    private fun validBalance(balance: Int?): Int? = balance?.takeIf { it >= 0 }

    private fun ActiveSession.toAppDataSession() = AppDataSession(user.id, generation)

    private fun AppDataSyncState.copy(
        balance: Int? = this.balance,
        homeRefreshRevision: Long = this.homeRefreshRevision,
        shopRefreshRevision: Long = this.shopRefreshRevision,
        inactiveProductRevisions: Map<String, Long> = this.inactiveProductRevisions,
    ) = AppDataSyncState(
        session = session,
        balance = balance,
        homeRefreshRevision = homeRefreshRevision,
        shopRefreshRevision = shopRefreshRevision,
        inactiveProductRevisions = inactiveProductRevisions,
    )
}
