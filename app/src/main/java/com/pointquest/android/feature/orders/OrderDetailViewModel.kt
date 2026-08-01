package com.pointquest.android.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.orders.OrdersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OrderDetailUiState(
    val order: Order? = null,
    val loading: Boolean = true,
    val error: UiText? = null,
)

class OrderDetailViewModel(
    private val orderId: String,
    private val repository: OrdersRepository,
    private val scopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OrderDetailUiState())
    private val initializationLock = Any()
    private var initialized = false

    val uiState: StateFlow<OrderDetailUiState> = mutableUiState
    var loadingJob: Job? = null
        private set

    fun initialize(): Job? = synchronized(initializationLock) {
        if (initialized) return@synchronized null
        initialized = true
        load()
    }

    fun retry(): Job? = if (loadingJob?.isActive == true) null else load()

    private fun load(): Job {
        mutableUiState.value = mutableUiState.value.copy(loading = true, error = null)
        return scope.launch {
            val first = repository.detail(orderId)
            val result = if (first is AppResult.Failure && first.error.code == ORDER_INVALID_STATUS) {
                repository.detail(orderId)
            } else {
                first
            }
            mutableUiState.value = when (result) {
                is AppResult.Success -> OrderDetailUiState(order = result.value, loading = false)
                is AppResult.Failure -> mutableUiState.value.copy(
                    loading = false, error = UiErrorMapper.map(result.error),
                )
            }
        }.also { loadingJob = it }
    }

    private val scope: CoroutineScope get() = scopeOverride ?: viewModelScope

    private companion object { const val ORDER_INVALID_STATUS = "ORDER_INVALID_STATUS" }
}
