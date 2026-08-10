package com.pointquest.android.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.PageAdjustment
import com.pointquest.android.core.network.PagedState
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.preferences.LearnerLanguageStore
import com.pointquest.android.data.practice.PracticeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class WrongQuestionsViewModel(
    private val repository: PracticeRepository,
    private val learnerLanguageStore: LearnerLanguageStore,
    private val scopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        WrongQuestionsUiState(language = learnerLanguageStore.language.value),
    )
    private val initializationLock = Any()
    private var initialized = false
    private var requestGeneration = 0

    val uiState: StateFlow<WrongQuestionsUiState> = mutableUiState

    var loadingJob: Job? = null
        private set
    var loadMoreJob: Job? = null
        private set

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            learnerLanguageStore.language
                .drop(1)
                .collect { language ->
                    if (!initialized) {
                        mutableUiState.value = mutableUiState.value.copy(language = language)
                        return@collect
                    }
                    load(language = language)
                }
        }
    }

    fun initialize(): Job? = synchronized(initializationLock) {
        if (initialized) return@synchronized null
        initialized = true
        load()
    }

    fun load(language: LearnerLanguage = learnerLanguageStore.language.value): Job {
        synchronized(initializationLock) {
            initialized = true
        }
        loadingJob?.cancel()
        loadMoreJob?.cancel()
        val generation = ++requestGeneration
        mutableUiState.value = mutableUiState.value.copy(
            language = language,
            paged = PagedState(),
            loading = true,
            loadingMore = false,
            error = null,
            loadMoreError = null,
        )
        return scope.launch {
            requestPage(
                page = FIRST_PAGE,
                initial = true,
                visited = mutableSetOf(),
                language = language,
                generation = generation,
            )
        }.also { loadingJob = it }
    }

    fun loadMore(): Job? {
        val state = mutableUiState.value
        if (!state.canLoadMore || loadMoreJob?.isActive == true || loadingJob?.isActive == true) return null
        val nextPage = state.paged.meta.page + 1
        mutableUiState.value = state.copy(loadingMore = true, loadMoreError = null)
        return scope.launch {
            requestPage(
                page = nextPage,
                initial = false,
                visited = mutableSetOf(),
                language = state.language,
                generation = requestGeneration,
            )
        }.also { loadMoreJob = it }
    }

    fun removeMastered(questionId: String): Job? {
        val state = mutableUiState.value
        val remaining = state.items.filterNot { it.question.id == questionId }
        if (remaining.size == state.items.size) return null

        val oldMeta = state.paged.meta
        val newTotal = (oldMeta.total - 1).coerceAtLeast(0)
        val newTotalPages = totalPages(newTotal, oldMeta.pageSize)
        val lastValidPage = newTotalPages.coerceAtLeast(FIRST_PAGE)
        val correctedMeta = oldMeta.copy(
            page = oldMeta.page.coerceAtMost(lastValidPage),
            total = newTotal,
            totalPages = newTotalPages,
        )
        mutableUiState.value = state.copy(
            paged = PagedState(remaining, correctedMeta),
            loadMoreError = null,
        )

        val needsFallback = newTotal > 0 &&
            (remaining.isEmpty() || oldMeta.page > lastValidPage)
        if (!needsFallback) return null

        val generation = ++requestGeneration
        mutableUiState.value = mutableUiState.value.copy(loading = true, error = null)
        return scope.launch {
            requestPage(
                page = lastValidPage,
                initial = true,
                visited = mutableSetOf(),
                language = mutableUiState.value.language,
                generation = generation,
            )
        }.also { loadingJob = it }
    }

    fun showDraftExpiredNotice() {
        mutableUiState.value = mutableUiState.value.copy(
            notice = UiText.Resource(R.string.practice_draft_expired),
        )
    }

    fun clearNotice() {
        mutableUiState.value = mutableUiState.value.copy(notice = null)
    }

    private suspend fun requestPage(
        page: Int,
        initial: Boolean,
        visited: MutableSet<Int>,
        language: LearnerLanguage,
        generation: Int,
    ) {
        if (!visited.add(page)) {
            if (!isLatestRequest(generation, language)) return
            finishWithAdjustmentError(initial)
            return
        }
        when (val result = repository.wrongQuestions(page, language)) {
            is AppResult.Success -> {
                if (!isLatestRequest(generation, language)) return
                val base = mutableUiState.value.paged.copy(adjustment = null)
                val merged = base.merge(result.value) { it.question.id }
                val adjustment = merged.adjustment
                if (adjustment is PageAdjustment.Reload) {
                    if (!isLatestRequest(generation, language)) return
                    mutableUiState.value = mutableUiState.value.copy(
                        paged = base,
                        loading = true,
                        loadingMore = false,
                    )
                    requestPage(
                        page = adjustment.lastValidPage,
                        initial = true,
                        visited = visited,
                        language = language,
                        generation = generation,
                    )
                } else {
                    mutableUiState.value = mutableUiState.value.copy(
                        language = language,
                        paged = merged,
                        loading = false,
                        loadingMore = false,
                        error = null,
                        loadMoreError = null,
                    )
                }
            }
            is AppResult.Failure -> {
                if (!isLatestRequest(generation, language)) return
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
        val message = UiText.Resource(R.string.wrong_questions_page_changed)
        mutableUiState.value = if (initial) {
            mutableUiState.value.copy(loading = false, loadingMore = false, error = message)
        } else {
            mutableUiState.value.copy(loadingMore = false, loadMoreError = message)
        }
    }

    private fun totalPages(total: Int, pageSize: Int): Int = when {
        total == 0 -> 0
        pageSize <= 0 -> FIRST_PAGE
        else -> (total + pageSize - 1) / pageSize
    }

    private fun isLatestRequest(generation: Int, language: LearnerLanguage): Boolean =
        generation == requestGeneration && learnerLanguageStore.language.value == language

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private companion object {
        const val FIRST_PAGE = 1
    }
}
