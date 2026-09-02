package com.pointquest.android.feature.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.PageAdjustment
import com.pointquest.android.core.network.PagedState
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.points.PointsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PointsUiState(
    val balance: Int? = null,
    val paged: PagedState<PointLedgerEntry> = PagedState(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
    val loadMoreError: UiText? = null,
) {
    val items: List<PointLedgerEntry> get() = paged.items
    val canLoadMore: Boolean get() = paged.canLoadMore && !loading && !loadingMore
    val empty: Boolean get() = !loading && error == null && items.isEmpty()
}

class PointsViewModel(
    private val repository: PointsRepository,
    private val scopeOverride: CoroutineScope? = null,
    private val appDataSync: AppDataSync? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PointsUiState())
    private val initializationLock = Any()
    private var initialized = false
    private var balanceFailed = true
    private var ledgerFailed = true
    private var ledgerError: UiText? = null

    val uiState: StateFlow<PointsUiState> = mutableUiState
    var loadingJob: Job? = null
        private set
    var loadMoreJob: Job? = null
        private set

    fun initialize(): Job? = synchronized(initializationLock) {
        if (initialized) return@synchronized null
        initialized = true
        observeAppDataSync()
        load(loadBalance = true, loadLedger = true)
    }

    fun retry(): Job? = if (loadingJob?.isActive == true) null else {
        load(loadBalance = balanceFailed, loadLedger = ledgerFailed)
    }

    fun loadMore(): Job? {
        val state = mutableUiState.value
        if (!state.canLoadMore || loadingJob?.isActive == true || loadMoreJob?.isActive == true) return null
        mutableUiState.value = state.copy(loadingMore = true, loadMoreError = null)
        return scope.launch {
            requestMore(state.paged.meta.page + 1, mutableSetOf())
        }.also { loadMoreJob = it }
    }

    private fun observeAppDataSync() {
        val sync = appDataSync ?: return
        scope.launch {
            sync.state.collect { state ->
                state.balance?.takeIf { state.session != null }?.let { balance ->
                    mutableUiState.value = mutableUiState.value.copy(balance = balance)
                }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sync.state
                .map { it.session to it.homeRefreshRevision }
                .distinctUntilChanged()
                .drop(1)
                .collect { (session, _) ->
                    if (session == null) return@collect
                    loadingJob?.cancel()
                    load(loadBalance = true, loadLedger = true)
                }
        }
    }

    private fun load(loadBalance: Boolean, loadLedger: Boolean): Job {
        mutableUiState.value = mutableUiState.value.copy(loading = true, error = null)
        return scope.launch {
            val results = coroutineScope {
                val balance = if (loadBalance) async { repository.balance() } else null
                val ledger = if (loadLedger) async { repository.ledger(FIRST_PAGE) } else null
                balance?.await() to ledger?.await()
            }
            results.first?.let(::applyBalance)
            results.second?.let { applyInitialLedger(it, mutableSetOf(FIRST_PAGE)) }
            mutableUiState.value = mutableUiState.value.copy(loading = false, error = loadError())
        }.also { loadingJob = it }
    }

    private fun applyBalance(result: AppResult<Int>) {
        when (result) {
            is AppResult.Success -> {
                balanceFailed = false
                val balance = result.value.coerceAtLeast(0)
                mutableUiState.value = mutableUiState.value.copy(balance = balance)
                appDataSync?.captureSession()?.let { session ->
                    appDataSync.recordBalance(session, balance)
                }
            }
            is AppResult.Failure -> balanceFailed = true
        }
    }

    private suspend fun applyInitialLedger(
        result: AppResult<Page<PointLedgerEntry>>,
        visited: MutableSet<Int>,
    ) {
        when (result) {
            is AppResult.Success -> {
                val base = PagedState<PointLedgerEntry>()
                val merged = base.merge(result.value, keySelector = PointLedgerEntry::id)
                val adjustment = merged.adjustment
                if (adjustment is PageAdjustment.Reload && visited.add(adjustment.lastValidPage)) {
                    applyInitialLedger(repository.ledger(adjustment.lastValidPage), visited)
                } else if (adjustment != null) {
                    ledgerFailed = true
                    ledgerError = UiText.Resource(R.string.points_page_changed)
                } else {
                    ledgerFailed = false
                    ledgerError = null
                    mutableUiState.value = mutableUiState.value.copy(paged = merged)
                }
            }
            is AppResult.Failure -> {
                ledgerFailed = true
                ledgerError = UiErrorMapper.map(result.error)
            }
        }
    }

    private suspend fun requestMore(page: Int, visited: MutableSet<Int>) {
        if (!visited.add(page)) {
            mutableUiState.value = mutableUiState.value.copy(
                loadingMore = false, loadMoreError = UiText.Resource(R.string.points_page_changed),
            )
            return
        }
        when (val result = repository.ledger(page)) {
            is AppResult.Success -> {
                val base = mutableUiState.value.paged.copy(adjustment = null)
                val merged = base.merge(result.value, keySelector = PointLedgerEntry::id)
                val adjustment = merged.adjustment
                if (adjustment is PageAdjustment.Reload) {
                    requestMore(adjustment.lastValidPage, visited)
                } else {
                    mutableUiState.value = mutableUiState.value.copy(
                        paged = merged, loadingMore = false, loadMoreError = null,
                    )
                }
            }
            is AppResult.Failure -> mutableUiState.value = mutableUiState.value.copy(
                loadingMore = false, loadMoreError = UiErrorMapper.map(result.error),
            )
        }
    }

    private fun loadError(): UiText? = when {
        ledgerFailed -> ledgerError
        balanceFailed -> UiText.Resource(R.string.points_balance_load_failed)
        else -> null
    }

    private val scope: CoroutineScope get() = scopeOverride ?: viewModelScope

    private companion object { const val FIRST_PAGE = 1 }
}
