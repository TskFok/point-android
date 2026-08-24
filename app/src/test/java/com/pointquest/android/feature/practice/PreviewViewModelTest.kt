package com.pointquest.android.feature.practice

import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.auth.ActiveSession
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.QuestionOption
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.practice.PracticeRepository
import com.pointquest.android.data.preferences.DefaultLearnerLanguageStore
import com.pointquest.android.test.MemoryLearnerLanguagePersistence
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PreviewViewModelTest {
    @Test
    fun startingWithFiveLoadsServerOrderAndCompletesWithCorrectSkippedAndPointsCounts() = runTest {
        val repository = PreviewTestRepository(
            previewResult = AppResult.Success(listOf(question("q1"), question("q2"), question("q3"))),
            answerFirstResults = arrayDequeOf(
                AppResult.Success(answer(correct = true, selectedOptionId = "o1", pointsAwarded = 5)),
                AppResult.Success(answer(correct = false, selectedOptionId = "o3", pointsAwarded = 0)),
            ),
        )
        val viewModel = PreviewViewModel(
            repository,
            DefaultLearnerLanguageStore(MemoryLearnerLanguagePersistence()),
            testScope(),
        )

        viewModel.selectCount(5)
        viewModel.startPreview()?.join()
        assertEquals(PreviewPhase.QUIZ, viewModel.uiState.value.phase)
        assertEquals(5, repository.previewCalls.single().count)
        assertEquals(LearnerLanguage.ALL, repository.previewCalls.single().language)
        assertEquals(listOf("q1", "q2", "q3"), viewModel.uiState.value.items.map { it.question.id })

        viewModel.selectOption("o1")
        viewModel.submitCurrent()?.join()
        val firstKey = repository.answerFirstCalls.single().key
        viewModel.goNext()
        repository.answerFirstResults.add(
            AppResult.Failure(AppError(409, "QUESTION_ALREADY_ANSWERED", "done", null)),
        )
        viewModel.selectOption("o2")
        viewModel.submitCurrent()?.join()
        viewModel.goNext()
        viewModel.selectOption("o3")
        viewModel.submitCurrent()?.join()

        assertEquals(PreviewPhase.SUMMARY, viewModel.uiState.value.phase)
        assertEquals(1, viewModel.uiState.value.correctCount)
        assertEquals(1, viewModel.uiState.value.skippedCount)
        assertEquals(5, viewModel.uiState.value.pointsEarned)
        assertEquals(listOf("q1", "q2", "q3"), repository.answerFirstCalls.map { it.questionId })
        assertNotEquals(firstKey, repository.answerFirstCalls[1].key)
        assertNotEquals(repository.answerFirstCalls[1].key, repository.answerFirstCalls[2].key)
    }

    @Test
    fun failedSubmitCanRetryWithTheSameSubmissionKeyAndResetReturnsToSetup() = runTest {
        val repository = PreviewTestRepository(
            previewResult = AppResult.Success(listOf(question("q1"))),
            answerFirstResults = arrayDequeOf(
                AppResult.Failure(AppError(null, "NETWORK_ERROR", "offline", null)),
                AppResult.Success(answer(correct = true, selectedOptionId = "o2", pointsAwarded = 5)),
            ),
        )
        val viewModel = PreviewViewModel(
            repository,
            DefaultLearnerLanguageStore(MemoryLearnerLanguagePersistence()),
            testScope(),
        )
        viewModel.startPreview()?.join()
        val key = viewModel.uiState.value.items.single().submissionKey

        viewModel.selectOption("o2")
        viewModel.submitCurrent()?.join()

        val failedItem = viewModel.uiState.value.items.single()
        assertEquals("o2", failedItem.selectedOptionId)
        assertEquals(key, failedItem.submissionKey)
        assertNotNull(failedItem.submitError)
        assertEquals(PreviewPhase.QUIZ, viewModel.uiState.value.phase)

        viewModel.retrySubmit()?.join()

        val answeredItem = viewModel.uiState.value.items.single()
        assertEquals(key, answeredItem.submissionKey)
        assertNull(answeredItem.submitError)
        assertEquals(PreviewPhase.SUMMARY, viewModel.uiState.value.phase)
        assertEquals(listOf(key, key), repository.answerFirstCalls.map { it.key })

        viewModel.resetSession()

        assertEquals(PreviewPhase.SETUP, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(0, viewModel.uiState.value.correctCount)
        assertEquals(0, viewModel.uiState.value.skippedCount)
        assertEquals(0, viewModel.uiState.value.pointsEarned)
    }

    @Test
    fun failedSubmitFreezesPayloadSoRetryCannotReuseKeyWithChangedOption() = runTest {
        val repository = PreviewTestRepository(
            previewResult = AppResult.Success(listOf(question("q1"))),
            answerFirstResults = arrayDequeOf(
                AppResult.Failure(AppError(null, "NETWORK_ERROR", "offline", null)),
                AppResult.Success(answer(correct = false, selectedOptionId = "o1", pointsAwarded = 0)),
            ),
        )
        val viewModel = PreviewViewModel(
            repository,
            DefaultLearnerLanguageStore(MemoryLearnerLanguagePersistence()),
            testScope(),
        )
        viewModel.startPreview()?.join()
        viewModel.selectOption("o1")
        viewModel.submitCurrent()?.join()
        val key = repository.answerFirstCalls.single().key

        viewModel.selectOption("o2")
        viewModel.retrySubmit()?.join()

        assertEquals("o1", viewModel.uiState.value.items.single().selectedOptionId)
        assertEquals(listOf("o1", "o1"), repository.answerFirstCalls.map { it.optionId })
        assertEquals(listOf(key, key), repository.answerFirstCalls.map { it.key })
    }

    @Test
    fun alreadyAnsweredItemCannotBeSubmittedAgainAfterNavigatingBack() = runTest {
        val repository = PreviewTestRepository(
            previewResult = AppResult.Success(listOf(question("q1"), question("q2"), question("q3"))),
            answerFirstResults = arrayDequeOf(
                AppResult.Failure(AppError(409, "QUESTION_ALREADY_ANSWERED", "done", null)),
                AppResult.Success(answer(correct = true, selectedOptionId = "o2", pointsAwarded = 5)),
            ),
        )
        val viewModel = PreviewViewModel(
            repository,
            DefaultLearnerLanguageStore(MemoryLearnerLanguagePersistence()),
            testScope(),
        )
        viewModel.startPreview()?.join()
        viewModel.selectOption("o1")
        viewModel.submitCurrent()?.join()
        viewModel.goNext()
        viewModel.selectOption("o2")
        viewModel.submitCurrent()?.join()

        viewModel.goPrevious()
        val duplicate = viewModel.submitCurrent()

        assertNull(duplicate)
        assertEquals(2, repository.answerFirstCalls.size)
        assertTrue(viewModel.uiState.value.items.first().alreadyAnswered)
        assertEquals("o1", viewModel.uiState.value.items.first().selectedOptionId)
        assertEquals(PreviewPhase.QUIZ, viewModel.uiState.value.phase)
    }

    @Test
    fun invalidCountDoesNotLoadAndLoadFailuresCanRetry() = runTest {
        val repository = PreviewTestRepository(
            previewResult = AppResult.Failure(AppError(null, "NETWORK_ERROR", "offline", null)),
        )
        val viewModel = PreviewViewModel(
            repository,
            DefaultLearnerLanguageStore(MemoryLearnerLanguagePersistence("fr")),
            testScope(),
        )

        viewModel.selectCount(51)
        assertNull(viewModel.startPreview())
        assertTrue(repository.previewCalls.isEmpty())

        viewModel.selectCount(1)
        viewModel.startPreview()?.join()
        assertFalse(viewModel.uiState.value.loading)
        assertNotNull(viewModel.uiState.value.loadError)
        assertEquals(LearnerLanguage.FR, repository.previewCalls.single().language)

        repository.previewResult = AppResult.Success(emptyList())
        viewModel.retryLoad()?.join()

        assertTrue(viewModel.uiState.value.emptyPool)
        assertEquals(PreviewPhase.SETUP, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun noUnansweredQuestionsUsesPreviewEmptyStateInsteadOfGenericLoadError() = runTest {
        val repository = PreviewTestRepository(
            previewResult = AppResult.Failure(AppError(404, "NO_UNANSWERED_QUESTIONS", "empty", null)),
        )
        val viewModel = PreviewViewModel(
            repository,
            DefaultLearnerLanguageStore(MemoryLearnerLanguagePersistence()),
            testScope(),
        )

        viewModel.startPreview()?.join()

        assertTrue(viewModel.uiState.value.emptyPool)
        assertNull(viewModel.uiState.value.loadError)
        assertEquals(PreviewPhase.SETUP, viewModel.uiState.value.phase)
    }

    @Test
    fun successfulPreviewAnswerPublishesBalanceThroughAppDataSync() = runTest {
        val sessionState = SessionState().apply {
            publish(
                ActiveSession(
                    user = com.pointquest.android.core.model.User(
                        "student-1",
                        "student",
                        com.pointquest.android.core.model.UserRole.STUDENT,
                        42,
                    ),
                    accessToken = "token",
                    accessTokenExpiresAt = Instant.parse("2030-01-01T00:05:00Z"),
                    generation = 1,
                    loginSessionId = 1,
                ),
            )
        }
        val appDataSync = AppDataSync(sessionState)
        val repository = PreviewTestRepository(
            previewResult = AppResult.Success(listOf(question("q1"))),
            answerFirstResults = arrayDequeOf(
                AppResult.Success(answer(correct = true, selectedOptionId = "o1", pointsAwarded = 5)),
            ),
        )
        val viewModel = PreviewViewModel(
            repository,
            DefaultLearnerLanguageStore(MemoryLearnerLanguagePersistence()),
            testScope(),
            appDataSync,
        )

        viewModel.startPreview()?.join()
        viewModel.selectOption("o1")
        viewModel.submitCurrent()?.join()

        assertEquals(105, appDataSync.state.value.balance)
        assertEquals(1L, appDataSync.state.value.homeRefreshRevision)
    }

    private data class PreviewCall(val count: Int, val language: LearnerLanguage)

    private data class AnswerFirstCall(val questionId: String, val optionId: String, val key: String?)

    private class PreviewTestRepository(
        var previewResult: AppResult<List<Question>>,
        val answerFirstResults: ArrayDeque<AppResult<AnswerResult>> = ArrayDeque(),
    ) : PracticeRepository {
        val previewCalls = mutableListOf<PreviewCall>()
        val answerFirstCalls = mutableListOf<AnswerFirstCall>()

        override suspend fun previewQuestions(
            count: Int,
            language: LearnerLanguage,
        ): AppResult<List<Question>> {
            previewCalls += PreviewCall(count, language)
            return previewResult
        }

        override suspend fun answerFirst(
            questionId: String,
            selectedOptionId: String,
            idempotencyKey: String?,
        ): AppResult<AnswerResult> {
            answerFirstCalls += AnswerFirstCall(questionId, selectedOptionId, idempotencyKey)
            return answerFirstResults.removeFirst()
        }

        override suspend fun summary(language: LearnerLanguage): AppResult<PracticeSummary> = error("unused")

        override suspend fun nextQuestion(
            excludeIds: List<String>,
            language: LearnerLanguage,
        ): AppResult<Question> = error("unused")

        override suspend fun wrongQuestions(
            page: Int,
            language: LearnerLanguage,
        ): AppResult<Page<WrongQuestion>> = error("unused")

        override suspend fun answerWrong(
            questionId: String,
            selectedOptionId: String,
            idempotencyKey: String?,
        ): AppResult<AnswerResult> = error("unused")
    }

    private companion object {
        fun TestScope.testScope(): TestScope = this

        fun question(id: String): Question = Question(
            id = id,
            stem = "$id stem",
            basePoints = 5,
            options = listOf(
                QuestionOption("o1", "A", "Alpha", 1),
                QuestionOption("o2", "B", "Beta", 2),
                QuestionOption("o3", "C", "Gamma", 3),
            ),
        )

        fun answer(
            correct: Boolean,
            selectedOptionId: String,
            pointsAwarded: Int,
        ) = AnswerResult(
            balance = 100 + pointsAwarded,
            correct = correct,
            correctOptionId = "o2",
            errorCount = if (correct) 0 else 1,
            explanation = "explanation",
            pointsAwarded = pointsAwarded,
            selectedOptionId = selectedOptionId,
        )

        fun <T> arrayDequeOf(vararg values: T) = ArrayDeque(values.toList())
    }
}
