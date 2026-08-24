package com.pointquest.android.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.app.PracticeMode
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.app.AppDataSession
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.data.practice.PracticeRepository
import com.pointquest.android.data.preferences.LearnerLanguageStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
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
    private val appDataSync: AppDataSync? = null,
    private val learnerLanguageStore: LearnerLanguageStore? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(QuestionUiState(mode = mode))
    private val eventChannel = Channel<QuestionEvent>(Channel.BUFFERED)
    private val loadGeneration = AtomicLong(0)
    private val loadLock = Any()
    private val initializationLock = Any()
    private var initialized = false
    private var activeLanguage: LearnerLanguage = learnerLanguageStore?.language?.value ?: LearnerLanguage.ALL

    val uiState: StateFlow<QuestionUiState> = mutableUiState
    val events = eventChannel.receiveAsFlow()

    var loadJob: Job? = null
        private set
    var submitJob: Job? = null
        private set

    init {
        if (mode == PracticeMode.FIRST && learnerLanguageStore != null) {
            scope.launch {
                learnerLanguageStore.language.collect { language ->
                    if (language == activeLanguage) return@collect
                    activeLanguage = language
                    if (initialized) {
                        loadFirstQuestion(language)
                    }
                }
            }
        }
    }

    fun initialize(): Job? = synchronized(initializationLock) {
        if (initialized) return@synchronized null
        initialized = true
        load()
    }

    fun load(): Job = when (mode) {
        PracticeMode.FIRST -> loadFirstQuestion()
        PracticeMode.WRONG -> loadWrongQuestion()
    }

    fun loadFirstQuestion(): Job = loadFirstQuestion(activeLanguage)

    private fun loadFirstQuestion(language: LearnerLanguage): Job = synchronized(loadLock) {
        loadJob?.cancel()
        submitJob?.cancel()
        val generation = loadGeneration.incrementAndGet()
        mutableUiState.value = mutableUiState.value.copy(
            language = language,
            loading = true,
            queue = emptyList(),
            currentIndex = 0,
            loadingNext = false,
            submitting = false,
            completed = false,
            error = null,
            tailError = null,
        )
        scope.launch {
            val result = repository.nextQuestion(emptyList(), language)
            if (!isLatestLoad(generation, language)) return@launch
            when (result) {
                is AppResult.Success -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        loading = false,
                        queue = listOf(newQueueItem(result.value)),
                        currentIndex = 0,
                        completed = false,
                    )
                }
                is AppResult.Failure -> {
                    if (result.error.code == NO_UNANSWERED_QUESTIONS) {
                        mutableUiState.value = mutableUiState.value.copy(
                            loading = false,
                            completed = true,
                            queue = emptyList(),
                            currentIndex = 0,
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
        submitJob?.cancel()
        loadGeneration.incrementAndGet()
        scope.launch {
            val draft = questionId?.let { draftStore?.consume(it) }
            if (draft == null) {
                mutableUiState.value = mutableUiState.value.copy(loading = false, queue = emptyList())
                eventChannel.send(QuestionEvent.DraftMissing)
            } else {
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    queue = listOf(newQueueItem(draft.question)),
                    currentIndex = 0,
                    error = null,
                )
            }
        }.also { loadJob = it }
    }

    fun selectOption(optionId: String) {
        val state = mutableUiState.value
        if (!state.selectionEnabled || state.question?.options?.none { it.id == optionId } != false) return
        updateCurrentItem(state) { item ->
            item.copy(selectedOptionId = optionId, submitError = null)
        }
    }

    fun submit(): Job? {
        val state = mutableUiState.value
        val index = state.currentIndex
        val item = state.currentItem ?: return null
        val question = item.question
        val selectedOptionId = item.submissionOptionId ?: item.selectedOptionId ?: return null
        if (!state.submitEnabled || submitJob?.isActive == true) return null
        val submissionGeneration = loadGeneration.get()
        val appDataSession = appDataSync?.captureSession()
        updateCurrentItem(state.copy(submitting = true, error = null, tailError = null)) { current ->
            current.copy(
                submissionOptionId = current.submissionOptionId ?: selectedOptionId,
                submitError = null,
            )
        }
        return scope.launch {
            val result = when (mode) {
                PracticeMode.FIRST -> repository.answerFirst(question.id, selectedOptionId, item.submissionKey)
                PracticeMode.WRONG -> repository.answerWrong(question.id, selectedOptionId, item.submissionKey)
            }
            if (!isCurrentSubmission(submissionGeneration, index, question.id)) return@launch
            when (result) {
                is AppResult.Success -> {
                    if (appDataSession != null) {
                        appDataSync?.recordPracticeChanged(appDataSession, result.value.balance)
                    }
                    updateCurrentItem(mutableUiState.value.copy(submitting = false)) { current ->
                        current.copy(result = result.value, submitError = null)
                    }
                    if (mode == PracticeMode.WRONG && result.value.correct) {
                        eventChannel.send(QuestionEvent.WrongMastered(question.id, returnToList = false))
                    }
                }
                is AppResult.Failure -> handleSubmitFailure(question.id, result, appDataSession)
            }
        }.also { submitJob = it }
    }

    fun retrySubmit(): Job? = submit()

    fun goPrevious() {
        val state = mutableUiState.value
        if (state.submitting || state.loading || state.loadingNext || !state.hasPrevious) return
        mutableUiState.value = state.copy(currentIndex = state.currentIndex - 1, tailError = null)
    }

    fun goNext(): Job? {
        val state = mutableUiState.value
        if (state.submitting || state.loading || state.loadingNext || state.completed) return null
        if (state.hasNextInQueue) {
            mutableUiState.value = state.copy(currentIndex = state.currentIndex + 1, tailError = null)
            return null
        }
        if (mode != PracticeMode.FIRST) return null
        return loadTailQuestion(activeLanguage)
    }

    fun retryTailLoad(): Job? {
        val state = mutableUiState.value
        if (mode != PracticeMode.FIRST || state.loading || state.loadingNext || state.submitting || state.tailError == null) {
            return null
        }
        return loadTailQuestion(activeLanguage)
    }

    private fun loadTailQuestion(language: LearnerLanguage): Job = synchronized(loadLock) {
        loadJob?.cancel()
        val generation = loadGeneration.incrementAndGet()
        val excludes = mutableUiState.value.queue.map { it.question.id }
        mutableUiState.value = mutableUiState.value.copy(
            loadingNext = true,
            tailError = null,
            error = null,
        )
        scope.launch {
            val result = repository.nextQuestion(excludes, language)
            if (!isLatestLoad(generation, language)) return@launch
            when (result) {
                is AppResult.Success -> {
                    val state = mutableUiState.value
                    mutableUiState.value = state.copy(
                        queue = state.queue + newQueueItem(result.value),
                        currentIndex = state.queue.size,
                        loading = false,
                        loadingNext = false,
                        completed = false,
                        tailError = null,
                    )
                }
                is AppResult.Failure -> {
                    if (result.error.code == NO_UNANSWERED_QUESTIONS) {
                        mutableUiState.value = mutableUiState.value.copy(
                            loading = false,
                            loadingNext = false,
                            completed = true,
                            tailError = null,
                        )
                    } else {
                        val mapped = UiErrorMapper.map(result.error)
                        val state = mutableUiState.value
                        mutableUiState.value = if (state.queue.isEmpty()) {
                            state.copy(loading = false, loadingNext = false, error = mapped)
                        } else {
                            state.copy(loading = false, loadingNext = false, tailError = mapped)
                        }
                    }
                }
            }
        }.also { loadJob = it }
    }

    private suspend fun handleSubmitFailure(
        questionId: String,
        result: AppResult.Failure,
        appDataSession: AppDataSession?,
    ) {
        when {
            mode == PracticeMode.FIRST && result.error.code == QUESTION_ALREADY_ANSWERED -> {
                if (appDataSession != null) appDataSync?.recordPracticeChanged(appDataSession)
                updateCurrentItem(mutableUiState.value.copy(submitting = false)) { current ->
                    current.copy(alreadyAnswered = true, submitError = null)
                }
                loadTailQuestion(activeLanguage)
            }
            mode == PracticeMode.WRONG && result.error.code == QUESTION_ALREADY_MASTERED -> {
                if (appDataSession != null) appDataSync?.recordPracticeChanged(appDataSession)
                mutableUiState.value = mutableUiState.value.copy(submitting = false)
                eventChannel.send(QuestionEvent.WrongMastered(questionId, returnToList = true))
            }
            else -> updateCurrentItem(mutableUiState.value.copy(submitting = false)) { current ->
                current.copy(submitError = UiErrorMapper.map(result.error))
            }
        }
    }

    private fun updateCurrentItem(
        state: QuestionUiState,
        transform: (QuestionQueueItem) -> QuestionQueueItem,
    ): QuestionUiState {
        val index = state.currentIndex
        val current = state.queue.getOrNull(index) ?: return state
        val updated = state.copy(
            queue = state.queue.toMutableList().also { items -> items[index] = transform(current) },
        )
        mutableUiState.value = updated
        return updated
    }

    private fun newQueueItem(question: com.pointquest.android.core.model.Question) =
        QuestionQueueItem(question = question, submissionKey = UUID.randomUUID().toString())

    private fun isLatestLoad(generation: Long, language: LearnerLanguage): Boolean =
        loadGeneration.get() == generation && activeLanguage == language

    private fun isCurrentSubmission(generation: Long, index: Int, questionId: String): Boolean =
        loadGeneration.get() == generation &&
            mutableUiState.value.queue.getOrNull(index)?.question?.id == questionId

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private companion object {
        const val QUESTION_ALREADY_ANSWERED = "QUESTION_ALREADY_ANSWERED"
        const val QUESTION_ALREADY_MASTERED = "QUESTION_ALREADY_MASTERED"
        const val NO_UNANSWERED_QUESTIONS = "NO_UNANSWERED_QUESTIONS"
    }
}
