package com.pointquest.android.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.data.practice.PracticeRepository
import com.pointquest.android.data.preferences.LearnerLanguageStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PreviewViewModel(
    private val repository: PracticeRepository,
    private val learnerLanguageStore: LearnerLanguageStore,
    private val scopeOverride: CoroutineScope? = null,
    private val appDataSync: AppDataSync? = null,
) : ViewModel() {
    private val mutableUiState = kotlinx.coroutines.flow.MutableStateFlow(PreviewUiState())

    val uiState: kotlinx.coroutines.flow.StateFlow<PreviewUiState> = mutableUiState

    private var loadJob: Job? = null
    private var submitJob: Job? = null

    fun selectCount(value: Int?) {
        if (mutableUiState.value.loading) return
        mutableUiState.value = mutableUiState.value.copy(count = value, loadError = null, emptyPool = false)
    }

    fun startPreview(): Job? {
        val count = mutableUiState.value.count ?: return null
        if (count !in PreviewUiState.MIN_PREVIEW_COUNT..PreviewUiState.MAX_PREVIEW_COUNT) return null
        if (loadJob?.isActive == true) return null
        val language = learnerLanguageStore.language.value
        mutableUiState.value = mutableUiState.value.copy(
            loading = true,
            loadError = null,
            emptyPool = false,
            language = language,
            items = emptyList(),
            currentIndex = 0,
            phase = PreviewPhase.SETUP,
            submitting = false,
        )
        return scope.launch {
            when (val result = repository.previewQuestions(count, language)) {
                is AppResult.Success -> {
                    val items = result.value.map { question ->
                        PreviewItem(question = question, submissionKey = UUID.randomUUID().toString())
                    }
                    mutableUiState.value = mutableUiState.value.copy(
                        loading = false,
                        items = items,
                        currentIndex = 0,
                        phase = if (items.isEmpty()) PreviewPhase.SETUP else PreviewPhase.QUIZ,
                        emptyPool = items.isEmpty(),
                    )
                }
                is AppResult.Failure -> {
                    if (result.error.code == NO_UNANSWERED_QUESTIONS) {
                        mutableUiState.value = mutableUiState.value.copy(
                            loading = false,
                            loadError = null,
                            emptyPool = true,
                            phase = PreviewPhase.SETUP,
                        )
                    } else {
                        mutableUiState.value = mutableUiState.value.copy(
                            loading = false,
                            loadError = UiErrorMapper.map(result.error),
                            emptyPool = false,
                            phase = PreviewPhase.SETUP,
                        )
                    }
                }
            }
        }.also { loadJob = it }
    }

    fun selectOption(optionId: String) {
        val state = mutableUiState.value
        val item = state.currentItem ?: return
        if (state.phase != PreviewPhase.QUIZ || state.submitting || item.answered || item.submissionOptionId != null) return
        if (item.question.options.none { it.id == optionId }) return
        updateCurrentItem(state) { it.copy(selectedOptionId = optionId, submitError = null) }
    }

    fun submitCurrent(): Job? {
        val state = mutableUiState.value
        val item = state.currentItem ?: return null
        val selectedOptionId = item.selectedOptionId ?: return null
        if (state.phase != PreviewPhase.QUIZ || state.submitting || item.answered || submitJob?.isActive == true) {
            return null
        }
        val submissionOptionId = item.submissionOptionId ?: selectedOptionId
        val appDataSession = appDataSync?.captureSession()
        updateCurrentItem(state.copy(submitting = true)) {
            it.copy(submissionOptionId = submissionOptionId, submitError = null)
        }
        return scope.launch {
            when (val result = repository.answerFirst(item.question.id, submissionOptionId, item.submissionKey)) {
                is AppResult.Success -> {
                    if (appDataSession != null) {
                        appDataSync?.recordPracticeChanged(appDataSession, result.value.balance)
                    }
                    val after = updateCurrentItem(mutableUiState.value.copy(submitting = false)) {
                        it.copy(result = result.value, submitError = null)
                    }
                    finishIfComplete(after)
                }
                is AppResult.Failure -> {
                    if (result.error.code == QUESTION_ALREADY_ANSWERED) {
                        val after = updateCurrentItem(mutableUiState.value.copy(submitting = false)) {
                            it.copy(alreadyAnswered = true, submitError = null)
                        }
                        finishIfComplete(after)
                    } else {
                        updateCurrentItem(mutableUiState.value.copy(submitting = false)) {
                            it.copy(submitError = UiErrorMapper.map(result.error))
                        }
                    }
                }
            }
        }.also { submitJob = it }
    }

    fun goPrevious() {
        val state = mutableUiState.value
        if (state.phase != PreviewPhase.QUIZ || state.submitting || state.currentIndex <= 0) return
        mutableUiState.value = state.copy(currentIndex = state.currentIndex - 1)
    }

    fun goNext(): Job? {
        val state = mutableUiState.value
        if (state.phase != PreviewPhase.QUIZ || state.submitting) return null
        if (state.items.isNotEmpty() && state.items.all { it.answered }) {
            mutableUiState.value = state.copy(phase = PreviewPhase.SUMMARY)
            return null
        }
        val current = state.currentItem ?: return null
        if (!current.answered || state.currentIndex >= state.items.lastIndex) return null
        mutableUiState.value = state.copy(currentIndex = state.currentIndex + 1)
        return null
    }

    fun retryLoad(): Job? = startPreview()

    fun retrySubmit(): Job? = submitCurrent()

    fun resetSession() {
        val count = mutableUiState.value.count
        mutableUiState.value = PreviewUiState(
            count = count,
            language = learnerLanguageStore.language.value,
        )
    }

    private fun updateCurrentItem(
        state: PreviewUiState,
        transform: (PreviewItem) -> PreviewItem,
    ): PreviewUiState {
        val index = state.currentIndex
        val current = state.items.getOrNull(index) ?: return state
        val updated = state.copy(
            items = state.items.toMutableList().also { items -> items[index] = transform(current) },
        )
        mutableUiState.value = updated
        return updated
    }

    private fun finishIfComplete(state: PreviewUiState) {
        if (state.items.isNotEmpty() && state.items.all { it.answered }) {
            mutableUiState.value = state.copy(phase = PreviewPhase.SUMMARY)
        }
    }

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private companion object {
        const val QUESTION_ALREADY_ANSWERED = "QUESTION_ALREADY_ANSWERED"
        const val NO_UNANSWERED_QUESTIONS = "NO_UNANSWERED_QUESTIONS"
    }
}
