package com.pointquest.android.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.app.AppDataSession
import com.pointquest.android.app.ShopRefreshSnapshot
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.PageAdjustment
import com.pointquest.android.core.network.PagedState
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.products.ProductsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ProductListViewModel(
    private val repository: ProductsRepository,
    private val scopeOverride: CoroutineScope? = null,
    private val appDataSync: AppDataSync? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ProductListUiState())
    private val searchInput = MutableStateFlow("")
    private val initializationLock = Any()
    private var initialized = false
    private var generation = 0L
    private var activeQuery: String? = null
    private var searchCollectorJob: Job? = null
    private var observedSyncSession: AppDataSession? = null
    private var observedShopRevision = 0L

    val uiState: StateFlow<ProductListUiState> = mutableUiState

    var loadingJob: Job? = null
        private set
    var loadMoreJob: Job? = null
        private set
    var refreshJob: Job? = null
        private set

    fun initialize(): Job? = synchronized(initializationLock) {
        if (initialized) return@synchronized null
        initialized = true
        searchCollectorJob = scope.launch {
            searchInput
                .drop(1)
                .map(::normalizeSearch)
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query ->
                    if (query != activeQuery) startFirstPage(query)
                }
        }
        appDataSync?.let { sync ->
            val initialSyncState = sync.state.value
            observedSyncSession = initialSyncState.session
            observedShopRevision = initialSyncState.shopRefreshRevision
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                sync.state.collect { syncState ->
                    removeInactiveProducts(syncState.inactiveProductIds)
                    val sessionChanged = syncState.session != observedSyncSession
                    val revisionAdvanced = !sessionChanged &&
                        syncState.shopRefreshRevision > observedShopRevision
                    observedSyncSession = syncState.session
                    observedShopRevision = syncState.shopRefreshRevision
                    when {
                        sessionChanged -> handleSessionChanged(syncState.session)
                        revisionAdvanced -> restartForShopRevision()
                    }
                }
            }
        }
        startFirstPage(normalizeSearch(searchInput.value))
    }

    fun updateSearch(value: String) {
        mutableUiState.value = mutableUiState.value.copy(search = value)
        searchInput.value = value
    }

    fun retry(): Job? {
        if (loadingJob?.isActive == true) return null
        return startFirstPage(activeQuery)
    }

    fun refresh(): Job? {
        val state = mutableUiState.value
        if (state.loading || state.refreshing) return null
        return startRefresh()
    }

    private fun startRefresh(): Job {
        val state = mutableUiState.value
        generation += 1
        val requestGeneration = generation
        loadingJob?.cancel()
        loadMoreJob?.cancel()
        refreshJob?.cancel()
        mutableUiState.value = state.copy(
            refreshing = true,
            loadingMore = false,
            refreshError = null,
            loadMoreError = null,
        )
        return scope.launch {
            requestPage(
                query = activeQuery,
                page = FIRST_PAGE,
                initial = false,
                requestGeneration = requestGeneration,
                visited = mutableSetOf(),
                refreshing = true,
                shopRefreshSnapshot = appDataSync?.captureShopRefresh(),
            )
        }.also { refreshJob = it }
    }

    fun clearRefreshError() {
        mutableUiState.value = mutableUiState.value.copy(refreshError = null)
    }

    fun loadMore(): Job? {
        val state = mutableUiState.value
        if (!state.canLoadMore || loadingJob?.isActive == true || loadMoreJob?.isActive == true) return null
        val requestGeneration = generation
        val nextPage = state.paged.meta.page + 1
        mutableUiState.value = state.copy(loadingMore = true, loadMoreError = null)
        return scope.launch {
            requestPage(
                query = activeQuery,
                page = nextPage,
                initial = false,
                requestGeneration = requestGeneration,
                visited = mutableSetOf(),
            )
        }.also { loadMoreJob = it }
    }

    private fun startFirstPage(query: String?): Job {
        activeQuery = query
        generation += 1
        val requestGeneration = generation
        loadingJob?.cancel()
        loadMoreJob?.cancel()
        refreshJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(
            paged = PagedState(),
            loading = true,
            loadingMore = false,
            refreshing = false,
            error = null,
            refreshError = null,
            loadMoreError = null,
        )
        return scope.launch {
            requestPage(
                query,
                FIRST_PAGE,
                initial = true,
                requestGeneration,
                mutableSetOf(),
                shopRefreshSnapshot = appDataSync?.captureShopRefresh(),
            )
        }.also { loadingJob = it }
    }

    private suspend fun requestPage(
        query: String?,
        page: Int,
        initial: Boolean,
        requestGeneration: Long,
        visited: MutableSet<Int>,
        refreshing: Boolean = false,
        shopRefreshSnapshot: ShopRefreshSnapshot? = null,
    ) {
        if (!visited.add(page)) {
            if (requestGeneration == generation) finishWithAdjustmentError(initial, refreshing)
            return
        }
        when (val result = repository.page(query, page)) {
            is AppResult.Success -> {
                if (requestGeneration != generation) return
                val base = mutableUiState.value.paged.copy(adjustment = null)
                val inactiveIds = when {
                    page == FIRST_PAGE && shopRefreshSnapshot != null ->
                        appDataSync?.inactiveProductIdsNotCoveredBy(shopRefreshSnapshot).orEmpty()
                    else -> appDataSync?.state?.value?.inactiveProductIds.orEmpty()
                }
                val visiblePage = result.value.copy(
                    items = result.value.items.filterNot { it.id in inactiveIds },
                )
                val merged = base.merge(visiblePage, keySelector = { it.id })
                val adjustment = merged.adjustment
                if (adjustment is PageAdjustment.Reload) {
                    mutableUiState.value = mutableUiState.value.copy(
                        paged = base,
                        loading = initial && !refreshing,
                        loadingMore = !initial && !refreshing,
                        refreshing = refreshing,
                    )
                    requestPage(
                        query,
                        adjustment.lastValidPage,
                        initial,
                        requestGeneration,
                        visited,
                        refreshing,
                        shopRefreshSnapshot,
                    )
                } else {
                    mutableUiState.value = mutableUiState.value.copy(
                        paged = merged,
                        loading = false,
                        loadingMore = false,
                        refreshing = false,
                        error = null,
                        refreshError = null,
                        loadMoreError = null,
                    )
                    if (page == FIRST_PAGE && shopRefreshSnapshot != null) {
                        appDataSync?.acknowledgeShopRefresh(shopRefreshSnapshot)
                    }
                }
            }
            is AppResult.Failure -> {
                if (requestGeneration != generation) return
                val message = UiErrorMapper.map(result.error)
                mutableUiState.value = if (refreshing) {
                    mutableUiState.value.copy(
                        refreshing = false,
                        refreshError = message,
                    )
                } else if (initial) {
                    mutableUiState.value.copy(loading = false, loadingMore = false, error = message)
                } else {
                    mutableUiState.value.copy(loadingMore = false, loadMoreError = message)
                }
            }
        }
    }

    private fun finishWithAdjustmentError(initial: Boolean, refreshing: Boolean = false) {
        val message = UiText.Resource(R.string.product_page_changed)
        mutableUiState.value = if (refreshing) {
            mutableUiState.value.copy(refreshing = false, refreshError = message)
        } else if (initial) {
            mutableUiState.value.copy(loading = false, loadingMore = false, error = message)
        } else {
            mutableUiState.value.copy(loadingMore = false, loadMoreError = message)
        }
    }

    private fun removeInactiveProducts(inactiveIds: Set<String>) {
        val state = mutableUiState.value
        val visible = state.items.filterNot { it.id in inactiveIds }
        if (visible.size != state.items.size) {
            mutableUiState.value = state.copy(paged = state.paged.copy(items = visible))
        }
    }

    private fun handleSessionChanged(session: AppDataSession?) {
        generation += 1
        loadingJob?.cancel()
        loadMoreJob?.cancel()
        refreshJob?.cancel()
        if (session == null) {
            mutableUiState.value = ProductListUiState(search = mutableUiState.value.search)
        } else if (mutableUiState.value.items.isEmpty()) {
            startFirstPage(activeQuery)
        } else {
            startRefresh()
        }
    }

    private fun restartForShopRevision() {
        if (mutableUiState.value.items.isEmpty()) {
            startFirstPage(activeQuery)
        } else {
            startRefresh()
        }
    }

    private fun normalizeSearch(value: String): String? = value.trim().takeUnless(String::isEmpty)

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private companion object {
        const val FIRST_PAGE = 1
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
