package com.pointquest.android.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.app.AppDataSession
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.orders.OrdersRepository
import com.pointquest.android.data.points.PointsRepository
import com.pointquest.android.data.products.ProductsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ProductDetailViewModel(
    private val productId: String,
    private val productsRepository: ProductsRepository,
    private val ordersRepository: OrdersRepository,
    private val pointsRepository: PointsRepository,
    private val scopeOverride: CoroutineScope? = null,
    private val appDataSync: AppDataSync? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ProductDetailUiState())
    private val eventChannel = Channel<ProductDetailEvent>(Channel.BUFFERED)
    private val initializationLock = Any()
    private var initialized = false
    private var productFailed = true
    private var balanceFailed = true
    private var activeRedeemIdempotencyKey: String? = null

    val uiState: StateFlow<ProductDetailUiState> = mutableUiState
    val events: Flow<ProductDetailEvent> = eventChannel.receiveAsFlow()

    var loadingJob: Job? = null
        private set
    var redeemJob: Job? = null
        private set

    fun initialize(): Job? = synchronized(initializationLock) {
        if (initialized) return@synchronized null
        initialized = true
        load(loadProduct = true, loadBalance = true)
    }

    fun retry(): Job? {
        if (loadingJob?.isActive == true) return null
        return load(loadProduct = productFailed, loadBalance = balanceFailed)
    }

    fun requestRedeemConfirmation() {
        if (!mutableUiState.value.canRedeem) return
        if (activeRedeemIdempotencyKey == null) {
            activeRedeemIdempotencyKey = UUID.randomUUID().toString()
        }
        mutableUiState.value = mutableUiState.value.copy(
            showRedeemConfirmation = true,
            message = null,
            redeemRetryPending = false,
        )
    }

    fun dismissRedeemConfirmation() {
        if (mutableUiState.value.redeeming) return
        activeRedeemIdempotencyKey = null
        mutableUiState.value = mutableUiState.value.copy(
            showRedeemConfirmation = false,
            redeemRetryPending = false,
        )
    }

    fun clearMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = null)
    }

    fun confirmRedeem(): Job? {
        val state = mutableUiState.value
        if (!state.showRedeemConfirmation || !state.canRedeem || redeemJob?.isActive == true) return null
        val appDataSession = appDataSync?.captureSession()
        val idempotencyKey = activeRedeemIdempotencyKey ?: UUID.randomUUID().toString().also {
            activeRedeemIdempotencyKey = it
        }
        mutableUiState.value = state.copy(
            showRedeemConfirmation = false,
            redeeming = true,
            redeemRetryPending = false,
            message = null,
        )
        return scope.launch {
            when (val result = ordersRepository.redeem(productId, idempotencyKey)) {
                is AppResult.Success -> {
                    activeRedeemIdempotencyKey = null
                    if (appDataSession != null) {
                        appDataSync?.recordOrderCreated(appDataSession, result.value.balance)
                    }
                    val product = mutableUiState.value.product
                    mutableUiState.value = mutableUiState.value.copy(
                        product = product?.copy(stock = (product.stock - 1).coerceAtLeast(0)),
                        balance = result.value.balance,
                        redeeming = false,
                    )
                    eventChannel.send(ProductDetailEvent.NavigateToOrder(result.value.id))
                }
                is AppResult.Failure -> applyRedeemFailure(result.error, appDataSession)
            }
        }.also { redeemJob = it }
    }

    private fun load(loadProduct: Boolean, loadBalance: Boolean): Job {
        mutableUiState.value = mutableUiState.value.copy(loading = true, error = null)
        return scope.launch {
            val results = coroutineScope {
                val product = if (loadProduct) async { productsRepository.detail(productId) } else null
                val balance = if (loadBalance) async { pointsRepository.balance() } else null
                product?.await() to balance?.await()
            }
            results.first?.let(::applyProductResult)
            results.second?.let(::applyBalanceResult)
            mutableUiState.value = mutableUiState.value.copy(
                loading = false,
                error = loadError(),
            )
        }.also { loadingJob = it }
    }

    private fun applyProductResult(result: AppResult<Product>) {
        when (result) {
            is AppResult.Success -> {
                productFailed = false
                mutableUiState.value = mutableUiState.value.copy(product = result.value)
            }
            is AppResult.Failure -> {
                productFailed = true
                productLoadError = UiErrorMapper.map(result.error)
            }
        }
    }

    private fun applyBalanceResult(result: AppResult<Int>) {
        when (result) {
            is AppResult.Success -> {
                balanceFailed = false
                mutableUiState.value = mutableUiState.value.copy(balance = result.value.coerceAtLeast(0))
            }
            is AppResult.Failure -> balanceFailed = true
        }
    }

    private var productLoadError: UiText? = null

    private fun loadError(): UiText? = when {
        productFailed -> productLoadError
        balanceFailed -> UiText.Resource(R.string.product_balance_load_failed)
        else -> null
    }

    private suspend fun applyRedeemFailure(
        error: AppError,
        appDataSession: AppDataSession?,
    ) {
        when (error.code) {
            OUT_OF_STOCK -> mutableUiState.value = mutableUiState.value.copy(
                product = mutableUiState.value.product?.copy(stock = 0),
                redeeming = false,
                message = UiText.Resource(R.string.product_out_of_stock),
            ).also { activeRedeemIdempotencyKey = null }
            INSUFFICIENT_POINTS -> mutableUiState.value = mutableUiState.value.copy(
                balance = safeBalance(error.details["balance"]),
                redeeming = false,
                message = UiText.Resource(R.string.product_insufficient_points),
            ).also { activeRedeemIdempotencyKey = null }
            PRODUCT_INACTIVE -> {
                activeRedeemIdempotencyKey = null
                if (appDataSession != null) {
                    appDataSync?.recordProductInactive(appDataSession, productId)
                }
                mutableUiState.value = mutableUiState.value.copy(
                    product = mutableUiState.value.product?.copy(isActive = false),
                    redeeming = false,
                    message = UiText.Resource(R.string.product_inactive),
                )
                eventChannel.send(ProductDetailEvent.ReturnToShop)
            }
            IDEMPOTENCY_CONFLICT -> {
                activeRedeemIdempotencyKey = null
                mutableUiState.value = mutableUiState.value.copy(
                    redeeming = false,
                    message = UiText.Resource(R.string.product_redeem_conflict),
                )
            }
            else -> mutableUiState.value = mutableUiState.value.copy(
                showRedeemConfirmation = true,
                redeeming = false,
                redeemRetryPending = true,
                message = UiErrorMapper.map(error),
            )
        }
    }

    private fun safeBalance(value: Any?): Int? = when (value) {
        is Byte -> value.toInt().takeIf { it >= 0 }
        is Short -> value.toInt().takeIf { it >= 0 }
        is Int -> value.takeIf { it >= 0 }
        is Long -> value.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
        is Float -> value.toDouble()
            .takeIf { it.isFinite() && it >= 0.0 && it % 1.0 == 0.0 && it <= Int.MAX_VALUE.toDouble() }
            ?.toInt()
        is Double -> value
            .takeIf { it.isFinite() && it >= 0.0 && it % 1.0 == 0.0 && it <= Int.MAX_VALUE.toDouble() }
            ?.toInt()
        else -> null
    }

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private companion object {
        const val OUT_OF_STOCK = "OUT_OF_STOCK"
        const val INSUFFICIENT_POINTS = "INSUFFICIENT_POINTS"
        const val PRODUCT_INACTIVE = "PRODUCT_INACTIVE"
        const val IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT"
    }
}
