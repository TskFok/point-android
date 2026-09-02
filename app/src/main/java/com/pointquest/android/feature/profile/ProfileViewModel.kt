package com.pointquest.android.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.User
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.PageAdjustment
import com.pointquest.android.core.network.PagedState
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.auth.AuthRepository
import com.pointquest.android.data.points.PointsRepository
import com.pointquest.android.data.preferences.LearnerLanguageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val language: LearnerLanguage = LearnerLanguage.ALL,
    val languagePersistenceError: UiText? = null,
    val showLogoutConfirmation: Boolean = false,
    val loggingOut: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
    val loadMoreError: UiText? = null,
    val paged: PagedState<PointLedgerEntry> = PagedState(),
    val ledgerReady: Boolean = false,
) {
    val items: List<PointLedgerEntry> get() = paged.items
    val canLoadMore: Boolean get() = paged.canLoadMore && !loading && !loadingMore
    val empty: Boolean get() = ledgerReady && !loading && error == null && items.isEmpty()
}

class ProfileViewModel(
    private val authRepository: AuthRepository,
    sessionState: SessionState,
    private val learnerLanguageStore: LearnerLanguageStore,
    private val scopeOverride: CoroutineScope? = null,
    private val appDataSync: AppDataSync? = null,
    private val pointsRepository: PointsRepository? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        ProfileUiState(language = learnerLanguageStore.language.value),
    )
    private val initializationLock = Any()
    private var initialized = false
    private var accountFromMe: User? = null
    private var overlayBalance: Int? = null
    private var sessionUser: User? = null
    private var userFailed = false
    private var balanceFailed = false
    private var ledgerFailed = false
    private var ledgerError: UiText? = null
    private var loadGeneration = 0

    val uiState: StateFlow<ProfileUiState> = mutableUiState
    var loadingJob: Job? = null
        private set
    var loadMoreJob: Job? = null
        private set

    init {
        scope.launch {
            learnerLanguageStore.language.collect { language ->
                mutableUiState.value = mutableUiState.value.copy(
                    language = language,
                    languagePersistenceError = null,
                )
            }
        }
        scope.launch {
            val syncState = appDataSync?.state
            if (syncState == null) {
                sessionState.status.collect { status ->
                    sessionUser = (status as? SessionStatus.SignedIn)?.user
                    publishDisplayedUser()
                }
            } else {
                sessionState.status.combine(syncState) { status, sync -> status to sync }
                    .collect { (status, sync) ->
                        val nextUser = (status as? SessionStatus.SignedIn)?.user
                        if (nextUser?.id != sessionUser?.id) overlayBalance = null
                        sessionUser = nextUser
                        sync.balance.takeIf { sync.session?.userId == sessionUser?.id }?.let { overlayBalance = it }
                        publishDisplayedUser()
                    }
            }
        }
    }

    fun initialize(): Job? = synchronized(initializationLock) {
        if (initialized) return@synchronized null
        initialized = true
        observeHomeRefresh()
        load(loadUser = true, loadBalance = pointsRepository != null, loadLedger = pointsRepository != null)
    }

    fun retry(): Job? {
        if (loadingJob?.isActive == true) return null
        return load(
            loadUser = userFailed,
            loadBalance = balanceFailed && pointsRepository != null,
            loadLedger = ledgerFailed && pointsRepository != null,
        )
    }

    fun loadMore(): Job? {
        val state = mutableUiState.value
        if (!state.canLoadMore || loadingJob?.isActive == true || loadMoreJob?.isActive == true) return null
        val repository = pointsRepository ?: return null
        mutableUiState.value = state.copy(loadingMore = true, loadMoreError = null)
        return scope.launch {
            requestMore(repository, state.paged.meta.page + 1, mutableSetOf())
        }.also { loadMoreJob = it }
    }

    fun setLanguage(language: LearnerLanguage) {
        if (language == mutableUiState.value.language) {
            mutableUiState.value = mutableUiState.value.copy(languagePersistenceError = null)
            return
        }
        if (!learnerLanguageStore.setLanguage(language)) {
            mutableUiState.value = mutableUiState.value.copy(
                languagePersistenceError = UiText.Resource(R.string.profile_language_save_failed),
            )
        }
    }

    fun requestLogout() {
        if (mutableUiState.value.loggingOut) return
        mutableUiState.value = mutableUiState.value.copy(showLogoutConfirmation = true)
    }

    fun dismissLogout() {
        if (mutableUiState.value.loggingOut) return
        mutableUiState.value = mutableUiState.value.copy(showLogoutConfirmation = false)
    }

    fun confirmLogout(): Job? {
        if (!mutableUiState.value.showLogoutConfirmation || mutableUiState.value.loggingOut) return null
        mutableUiState.value = mutableUiState.value.copy(loggingOut = true)
        return scope.launch {
            try {
                authRepository.logout()
            } finally {
                mutableUiState.value = mutableUiState.value.copy(
                    showLogoutConfirmation = false,
                    loggingOut = false,
                )
            }
        }
    }

    private fun observeHomeRefresh() {
        val sync = appDataSync ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sync.state
                .map { it.session to it.homeRefreshRevision }
                .distinctUntilChanged()
                .drop(1)
                .collect { (session, _) ->
                    if (session == null) return@collect
                    loadingJob?.cancel()
                    load(
                        loadUser = true,
                        loadBalance = pointsRepository != null,
                        loadLedger = pointsRepository != null,
                    )
                }
        }
    }

    private fun load(loadUser: Boolean, loadBalance: Boolean, loadLedger: Boolean): Job {
        val generation = ++loadGeneration
        mutableUiState.value = mutableUiState.value.copy(loading = true, error = null)
        return scope.launch {
            val results = coroutineScope {
                val user = if (loadUser) async { authRepository.currentUser() } else null
                val balance = if (loadBalance) async { pointsRepository?.balance() } else null
                val ledger = if (loadLedger) async { pointsRepository?.ledger(FIRST_PAGE) } else null
                Triple(user?.await(), balance?.await(), ledger?.await())
            }
            if (!isLatestLoad(generation)) return@launch
            results.first?.let(::applyUser)
            results.second?.let(::applyBalance)
            results.third?.let { applyInitialLedger(it, mutableSetOf(FIRST_PAGE), generation) }
            if (!isLatestLoad(generation)) return@launch
            mutableUiState.value = mutableUiState.value.copy(
                loading = false,
                error = loadError(),
                ledgerReady = true,
            )
        }.also { loadingJob = it }
    }

    private fun applyUser(result: AppResult<User>) {
        when (result) {
            is AppResult.Success -> {
                userFailed = false
                accountFromMe = result.value
                overlayBalance = result.value.pointsBalance
                publishDisplayedUser()
            }
            is AppResult.Failure -> userFailed = true
        }
    }

    private fun applyBalance(result: AppResult<Int>) {
        when (result) {
            is AppResult.Success -> {
                balanceFailed = false
                overlayBalance = result.value.coerceAtLeast(0)
                appDataSync?.captureSession()?.let { session ->
                    appDataSync.recordBalance(session, overlayBalance ?: return)
                }
                publishDisplayedUser()
            }
            is AppResult.Failure -> balanceFailed = true
        }
    }

    private suspend fun applyInitialLedger(
        result: AppResult<Page<PointLedgerEntry>>,
        visited: MutableSet<Int>,
        generation: Int,
    ) {
        when (result) {
            is AppResult.Success -> {
                val merged = PagedState<PointLedgerEntry>().merge(result.value, keySelector = PointLedgerEntry::id)
                val adjustment = merged.adjustment
                if (adjustment is PageAdjustment.Reload && visited.add(adjustment.lastValidPage)) {
                    val repository = pointsRepository ?: return
                    applyInitialLedger(repository.ledger(adjustment.lastValidPage), visited, generation)
                } else if (adjustment != null) {
                    ledgerFailed = true
                    ledgerError = UiText.Resource(R.string.points_page_changed)
                } else if (isLatestLoad(generation)) {
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

    private suspend fun requestMore(repository: PointsRepository, page: Int, visited: MutableSet<Int>) {
        if (!visited.add(page)) {
            mutableUiState.value = mutableUiState.value.copy(
                loadingMore = false,
                loadMoreError = UiText.Resource(R.string.points_page_changed),
            )
            return
        }
        when (val result = repository.ledger(page)) {
            is AppResult.Success -> {
                val base = mutableUiState.value.paged.copy(adjustment = null)
                val merged = base.merge(result.value, keySelector = PointLedgerEntry::id)
                val adjustment = merged.adjustment
                if (adjustment is PageAdjustment.Reload) {
                    requestMore(repository, adjustment.lastValidPage, visited)
                } else {
                    mutableUiState.value = mutableUiState.value.copy(
                        paged = merged,
                        loadingMore = false,
                        loadMoreError = null,
                    )
                }
            }
            is AppResult.Failure -> mutableUiState.value = mutableUiState.value.copy(
                loadingMore = false,
                loadMoreError = UiErrorMapper.map(result.error),
            )
        }
    }

    private fun publishDisplayedUser() {
        val session = sessionUser
        if (session == null) {
            accountFromMe = null
            overlayBalance = null
            mutableUiState.value = mutableUiState.value.copy(user = null)
            return
        }
        if (accountFromMe?.id != session.id) accountFromMe = null
        val base = accountFromMe ?: session
        val user = overlayBalance?.let { base.copy(pointsBalance = it) } ?: base
        mutableUiState.value = mutableUiState.value.copy(user = user)
    }

    private fun loadError(): UiText? = when {
        ledgerFailed -> ledgerError
        userFailed && sessionUser == null && accountFromMe == null ->
            UiText.Resource(R.string.profile_account_refresh_failed)
        else -> null
    }

    private fun isLatestLoad(generation: Int): Boolean = generation == loadGeneration

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private companion object {
        const val FIRST_PAGE = 1
    }
}
