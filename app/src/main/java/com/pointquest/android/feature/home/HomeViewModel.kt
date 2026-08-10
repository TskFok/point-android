package com.pointquest.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.points.PointsRepository
import com.pointquest.android.data.practice.PracticeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeViewModel(
    private val practiceRepository: PracticeRepository,
    private val pointsRepository: PointsRepository,
    sessionState: SessionState,
    private val scopeOverride: CoroutineScope? = null,
    private val appDataSync: AppDataSync? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    private var summaryFailed = true
    private var balanceFailed = true
    private var sessionBalance: Int? = null

    val uiState: StateFlow<HomeUiState> = mutableUiState
    var loadingJob: Job? = null
        private set

    init {
        scope.launch {
            sessionState.status.collect { status ->
                if (status is SessionStatus.SignedIn) {
                    sessionBalance = status.user.pointsBalance
                    mutableUiState.value = mutableUiState.value.copy(
                        username = status.user.username,
                        balance = currentPreferredBalance(),
                    )
                }
            }
        }
        appDataSync?.let { sync ->
            scope.launch {
                sync.state.collect { state ->
                    val activeUserId = sessionState.active.value?.user?.id
                    if (state.session?.userId == activeUserId && state.balance != null) {
                        mutableUiState.value = mutableUiState.value.copy(balance = state.balance)
                    }
                }
            }
            scope.launch {
                sync.state
                    .map { it.session to it.homeRefreshRevision }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { (session, _) ->
                        if (session == null) return@collect
                        loadingJob?.cancel()
                        load(loadSummary = true, loadBalance = true)
                    }
            }
        }
        load(loadSummary = true, loadBalance = true)
    }

    fun retry(): Job? {
        if (loadingJob?.isActive == true) return null
        return load(loadSummary = summaryFailed, loadBalance = balanceFailed)
    }

    private fun load(loadSummary: Boolean, loadBalance: Boolean): Job {
        mutableUiState.value = mutableUiState.value.copy(loading = true, error = null, canRetry = false)
        return scope.launch {
            val results = coroutineScope {
                val summary = if (loadSummary) async { practiceRepository.summary() } else null
                val balance = if (loadBalance) async { pointsRepository.balance() } else null
                summary?.await() to balance?.await()
            }
            results.first?.let(::applySummaryResult)
            results.second?.let(::applyBalanceResult)
            mutableUiState.value = mutableUiState.value.copy(
                balance = currentPreferredBalance(),
                loading = false,
                error = errorMessage(),
                canRetry = summaryFailed || balanceFailed,
            )
        }.also { loadingJob = it }
    }

    private fun applySummaryResult(result: AppResult<PracticeSummary>) {
        when (result) {
            is AppResult.Success -> {
                summaryFailed = false
                mutableUiState.value = mutableUiState.value.copy(summary = result.value)
            }
            is AppResult.Failure -> summaryFailed = true
        }
    }

    private fun applyBalanceResult(result: AppResult<Int>) {
        when (result) {
            is AppResult.Success -> {
                balanceFailed = false
                mutableUiState.value = mutableUiState.value.copy(balance = result.value)
            }
            is AppResult.Failure -> balanceFailed = true
        }
    }

    private fun currentPreferredBalance(): Int? = when {
        !balanceFailed -> mutableUiState.value.balance
        !summaryFailed -> mutableUiState.value.summary?.balance
        else -> sessionBalance
    }

    private fun errorMessage(): UiText? = when {
        summaryFailed && balanceFailed -> UiText.Resource(R.string.home_error_load)
        summaryFailed -> UiText.Resource(R.string.home_error_summary)
        balanceFailed -> UiText.Resource(R.string.home_error_balance)
        else -> null
    }

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope
}
