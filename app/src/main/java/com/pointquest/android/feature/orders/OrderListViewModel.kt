package com.pointquest.android.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.PageAdjustment
import com.pointquest.android.core.network.PagedState
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.orders.OrdersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OrderListUiState(
    val paged: PagedState<Order> = PagedState(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
    val loadMoreError: UiText? = null,
) {
    val items: List<Order> get() = paged.items
    val canLoadMore: Boolean get() = paged.canLoadMore && !loading && !loadingMore
    val empty: Boolean get() = !loading && error == null && items.isEmpty()
}

class OrderListViewModel(
    private val repository: OrdersRepository,
    private val scopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OrderListUiState())
    private val initializationLock = Any()
    private var initialized = false

    val uiState: StateFlow<OrderListUiState> = mutableUiState
    var loadingJob: Job? = null
        private set
    var loadMoreJob: Job? = null
        private set

    fun initialize(): Job? = synchronized(initializationLock) {
        if (initialized) return@synchronized null
        initialized = true
        load()
    }

    fun retry(): Job? = if (loadingJob?.isActive == true) null else load()

    fun loadMore(): Job? {
        val state = mutableUiState.value
        if (!state.canLoadMore || loadingJob?.isActive == true || loadMoreJob?.isActive == true) return null
        mutableUiState.value = state.copy(loadingMore = true, loadMoreError = null)
        return scope.launch {
            requestPage(state.paged.meta.page + 1, initial = false, mutableSetOf())
        }.also { loadMoreJob = it }
    }

    private fun load(): Job {
        loadMoreJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(
            paged = PagedState(), loading = true, loadingMore = false, error = null, loadMoreError = null,
        )
        return scope.launch { requestPage(FIRST_PAGE, initial = true, mutableSetOf()) }
            .also { loadingJob = it }
    }

    private suspend fun requestPage(page: Int, initial: Boolean, visited: MutableSet<Int>) {
        if (!visited.add(page)) {
            finishWithAdjustmentError(initial)
            return
        }
        when (val result = repository.page(page)) {
            is AppResult.Success -> {
                val base = mutableUiState.value.paged.copy(adjustment = null)
                val merged = base.merge(result.value, keySelector = Order::id)
                val adjustment = merged.adjustment
                if (adjustment is PageAdjustment.Reload) {
                    mutableUiState.value = mutableUiState.value.copy(paged = base)
                    requestPage(adjustment.lastValidPage, initial, visited)
                } else {
                    mutableUiState.value = mutableUiState.value.copy(
                        paged = merged, loading = false, loadingMore = false, error = null, loadMoreError = null,
                    )
                }
            }
            is AppResult.Failure -> {
                val message = UiErrorMapper.map(result.error)
                mutableUiState.value = if (initial) {
                    mutableUiState.value.copy(loading = false, loadingMore = false, error = message)
                } else {
                    mutableUiState.value.copy(loadingMore = false, loadMoreError = message)
                }
            }
        }
    }

    private fun finishWithAdjustmentError(initial: Boolean) {
        val message = UiText.Resource(R.string.orders_page_changed)
        mutableUiState.value = if (initial) {
            mutableUiState.value.copy(loading = false, error = message)
        } else {
            mutableUiState.value.copy(loadingMore = false, loadMoreError = message)
        }
    }

    private val scope: CoroutineScope get() = scopeOverride ?: viewModelScope

    private companion object { const val FIRST_PAGE = 1 }
}
