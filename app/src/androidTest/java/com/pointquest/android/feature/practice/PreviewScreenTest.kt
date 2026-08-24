package com.pointquest.android.feature.practice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.QuestionOption
import com.pointquest.android.core.ui.theme.PointQuestTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun summaryOffersPracticeProfileAndResetActions() {
        var resetOpened = false
        var practiceOpened = false
        var profileOpened = false
        var homeOpened = false
        composeRule.setContent {
            PointQuestTheme {
                PreviewScreen(
                    state = PreviewUiState(
                        phase = PreviewPhase.SUMMARY,
                        items = listOf(
                            PreviewItem(
                                question = question,
                                selectedOptionId = "o1",
                                submissionOptionId = "o1",
                                submissionKey = "preview-key",
                                result = answer,
                            ),
                        ),
                    ),
                    onCountChange = {},
                    onStart = {},
                    onSelectOption = {},
                    onSubmit = {},
                    onPrevious = {},
                    onNext = {},
                    onRetryLoad = {},
                    onRetrySubmit = {},
                    onReset = { resetOpened = true },
                    onPractice = { practiceOpened = true },
                    onProfile = { profileOpened = true },
                    onHome = { homeOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("再来一轮").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("返回练习").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("我的").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("返回学习首页").assertIsDisplayed().performClick()
        assertTrue(resetOpened)
        assertTrue(practiceOpened)
        assertTrue(profileOpened)
        assertTrue(homeOpened)
    }

    @Test
    fun selectedLanguageEmptyPoolOffersWrongQuestionsAndProfileActions() {
        var wrongQuestionsOpened = false
        var profileOpened = false
        composeRule.setContent {
            PointQuestTheme {
                PreviewScreen(
                    state = PreviewUiState(
                        language = LearnerLanguage.FR,
                        emptyPool = true,
                    ),
                    onCountChange = {},
                    onStart = {},
                    onSelectOption = {},
                    onSubmit = {},
                    onPrevious = {},
                    onNext = {},
                    onRetryLoad = {},
                    onRetrySubmit = {},
                    onReset = {},
                    onWrongQuestions = { wrongQuestionsOpened = true },
                    onProfile = { profileOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("去错题本").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("调整学习语言").assertIsDisplayed().performClick()
        assertTrue(wrongQuestionsOpened)
        assertTrue(profileOpened)
    }

    private companion object {
        val question = Question(
            id = "q-preview",
            stem = "1 + 1 等于多少？",
            basePoints = 5,
            options = listOf(
                QuestionOption("o1", "A", "2", 1),
                QuestionOption("o2", "B", "3", 2),
            ),
        )
        val answer = AnswerResult(
            balance = 50,
            correct = true,
            correctOptionId = "o1",
            errorCount = 0,
            explanation = "1 + 1 = 2",
            pointsAwarded = 5,
            selectedOptionId = "o1",
        )
    }
}
