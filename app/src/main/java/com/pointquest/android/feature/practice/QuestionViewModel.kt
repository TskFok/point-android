package com.pointquest.android.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.app.PracticeMode
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.data.practice.PracticeRepository
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

fun interface PracticeDraftSource {
    fun consume(questionId: String): WrongQuestion?
}

class QuestionViewModel(
    private val repository: PracticeRepository,
    private val mode: PracticeMode,
    private val draftStore: PracticeDraftSource?,
    private val questionId: String?,
    private val scopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(QuestionUiState(mode = mode))
    private val eventChannel = Channel<QuestionEvent>(Channel.BUFFERED)
    private val loadGeneration = AtomicLong(0)
    private val shownQuestionIds = LinkedHashSet<String>()
    private val loadLock = Any()

    val uiState: StateFlow<QuestionUiState> = mutableUiState
    val events = eventChannel.receiveAsFlow()

    var loadJob: Job? = null
        private set
    var submitJob: Job? = null
        private set

    fun load(): Job = when (mode) {
        PracticeMode.FIRST -> loadFirstQuestion()
        PracticeMode.WRONG -> loadWrongQuestion()
    }

    fun loadFirstQuestion(): Job = synchronized(loadLock) {
        loadJob?.cancel()
        val generation = loadGeneration.incrementAndGet()
        val excludes = synchronized(shownQuestionIds) { shownQuestionIds.toList() }
        mutableUiState.value = mutableUiState.value.copy(
            loading = true,
            question = null,
            selectedOptionId = null,
            submitting = false,
            submitted = false,
            result = null,
            completed = false,
            error = null,
        )
        scope.launch {
            val result = repository.nextQuestion(excludes)
            if (loadGeneration.get() != generation) return@launch
            when (result) {
                is AppResult.Success -> {
                    recordShown(result.value.id)
                    mutableUiState.value = mutableUiState.value.copy(
                        loading = false,
                        question = result.value,
                    )
                }
                is AppResult.Failure -> {
                    if (result.error.code == NO_UNANSWERED_QUESTIONS) {
                        mutableUiState.value = mutableUiState.value.copy(
                            loading = false,
                            completed = true,
                            question = null,
                        )
                    } else {
                        mutableUiState.value = mutableUiState.value.copy(
                            loading = false,
                            error = UiErrorMapper.map(result.error),
                        )
                    }
                }
            }
        }.also { loadJob = it }
    }

    private fun loadWrongQuestion(): Job = synchronized(loadLock) {
        loadJob?.cancel()
        loadGeneration.incrementAndGet()
        scope.launch {
            val draft = questionId?.let { draftStore?.consume(it) }
            if (draft == null) {
                mutableUiState.value = mutableUiState.value.copy(loading = false, question = null)
                eventChannel.send(QuestionEvent.DraftMissing)
            } else {
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    question = draft.question,
                    error = null,
                )
            }
        }.also { loadJob = it }
    }

    fun selectOption(optionId: String) {
        val state = mutableUiState.value
        if (!state.selectionEnabled || state.question?.options?.none { it.id == optionId } != false) return
        mutableUiState.value = state.copy(selectedOptionId = optionId)
    }

    fun submit(): Job? {
        val state = mutableUiState.value
        val question = state.question ?: return null
        val selectedOptionId = state.selectedOptionId ?: return null
        if (!state.selectionEnabled || submitJob?.isActive == true) return null
        val submissionGeneration = loadGeneration.get()
        mutableUiState.value = state.copy(submitting = true, error = null)
        return scope.launch {
            val result = when (mode) {
                PracticeMode.FIRST -> repository.answerFirst(question.id, selectedOptionId)
                PracticeMode.WRONG -> repository.answerWrong(question.id, selectedOptionId)
            }
            if (
                loadGeneration.get() != submissionGeneration ||
                mutableUiState.value.question?.id != question.id
            ) return@launch
            when (result) {
                is AppResult.Success -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        submitting = false,
                        submitted = true,
                        result = result.value,
                    )
                    if (mode == PracticeMode.WRONG && result.value.correct) {
                        eventChannel.send(QuestionEvent.WrongMastered(question.id, returnToList = false))
                    }
                }
                is AppResult.Failure -> handleSubmitFailure(question.id, result)
            }
        }.also { submitJob = it }
    }

    private suspend fun handleSubmitFailure(questionId: String, result: AppResult.Failure) {
        when {
            mode == PracticeMode.FIRST && result.error.code == QUESTION_ALREADY_ANSWERED -> {
                mutableUiState.value = mutableUiState.value.copy(submitting = false)
                loadFirstQuestion()
            }
            mode == PracticeMode.WRONG && result.error.code == QUESTION_ALREADY_MASTERED -> {
                mutableUiState.value = mutableUiState.value.copy(submitting = false)
                eventChannel.send(QuestionEvent.WrongMastered(questionId, returnToList = true))
            }
            else -> mutableUiState.value = mutableUiState.value.copy(
                submitting = false,
                error = UiErrorMapper.map(result.error),
            )
        }
    }

    private fun recordShown(questionId: String) = synchronized(shownQuestionIds) {
        shownQuestionIds.remove(questionId)
        shownQuestionIds.add(questionId)
        while (shownQuestionIds.size > MAX_EXCLUDE_IDS) {
            shownQuestionIds.remove(shownQuestionIds.first())
        }
    }

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private companion object {
        const val MAX_EXCLUDE_IDS = 50
        const val QUESTION_ALREADY_ANSWERED = "QUESTION_ALREADY_ANSWERED"
        const val QUESTION_ALREADY_MASTERED = "QUESTION_ALREADY_MASTERED"
        const val NO_UNANSWERED_QUESTIONS = "NO_UNANSWERED_QUESTIONS"
    }
}
