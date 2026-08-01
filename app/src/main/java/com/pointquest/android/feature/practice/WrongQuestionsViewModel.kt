package com.pointquest.android.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.PageAdjustment
import com.pointquest.android.core.network.PagedState
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.practice.PracticeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WrongQuestionsViewModel(
    private val repository: PracticeRepository,
    private val scopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(WrongQuestionsUiState())
    private val initializationLock = Any()
    private var initialized = false

    val uiState: StateFlow<WrongQuestionsUiState> = mutableUiState

    var loadingJob: Job? = null
        private set
    var loadMoreJob: Job? = null
        private set

    fun initialize(): Job? = synchronized(initializationLock) {
        if (initialized) return@synchronized null
        initialized = true
        load()
    }

    fun load(): Job {
        loadingJob?.cancel()
        loadMoreJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(
            paged = PagedState(),
            loading = true,
            loadingMore = false,
            error = null,
            loadMoreError = null,
        )
        return scope.launch {
            requestPage(FIRST_PAGE, initial = true, visited = mutableSetOf())
        }.also { loadingJob = it }
    }

    fun loadMore(): Job? {
        val state = mutableUiState.value
        if (!state.canLoadMore || loadMoreJob?.isActive == true || loadingJob?.isActive == true) return null
        val nextPage = state.paged.meta.page + 1
        mutableUiState.value = state.copy(loadingMore = true, loadMoreError = null)
        return scope.launch {
            requestPage(nextPage, initial = false, visited = mutableSetOf())
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

        mutableUiState.value = mutableUiState.value.copy(loading = true, error = null)
        return scope.launch {
            requestPage(lastValidPage, initial = true, visited = mutableSetOf())
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

    private suspend fun requestPage(page: Int, initial: Boolean, visited: MutableSet<Int>) {
        if (!visited.add(page)) {
            finishWithAdjustmentError(initial)
            return
        }
        when (val result = repository.wrongQuestions(page)) {
            is AppResult.Success -> {
                val base = mutableUiState.value.paged.copy(adjustment = null)
                val merged = base.merge(result.value) { it.question.id }
                val adjustment = merged.adjustment
                if (adjustment is PageAdjustment.Reload) {
                    mutableUiState.value = mutableUiState.value.copy(
                        paged = base,
                        loading = true,
                        loadingMore = false,
                    )
                    requestPage(adjustment.lastValidPage, initial = true, visited = visited)
                } else {
                    mutableUiState.value = mutableUiState.value.copy(
                        paged = merged,
                        loading = false,
                        loadingMore = false,
                        error = null,
                        loadMoreError = null,
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

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private companion object {
        const val FIRST_PAGE = 1
    }
}
