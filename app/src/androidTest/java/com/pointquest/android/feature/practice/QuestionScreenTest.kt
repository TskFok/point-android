package com.pointquest.android.feature.practice

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.app.PracticeMode
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.QuestionOption
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.core.ui.theme.PointQuestTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun submitIsDisabledUntilAnOptionIsSelectedAndMeetsTouchTarget() {
        composeRule.setContent {
            PointQuestTheme {
                QuestionScreen(
                    state = QuestionUiState(
                        mode = PracticeMode.FIRST,
                        loading = false,
                        queue = listOf(queueItem(question)),
                    ),
                    onSelectOption = {},
                    onSubmit = {},
                    onPrevious = {},
                    onNext = {},
                    onRetry = {},
                    onRetryTailLoad = {},
                )
            }
        }

        composeRule.onNodeWithTag("question_submit")
            .assertHeightIsAtLeast(48.dp)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("question_option_o1").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun submittedAnswerUsesTextIconAndSemanticsForCorrectAndWrongOptions() {
        composeRule.setContent {
            PointQuestTheme {
                QuestionScreen(
                    state = QuestionUiState(
                        mode = PracticeMode.FIRST,
                        loading = false,
                        queue = listOf(
                            queueItem(
                                question = question,
                                selectedOptionId = "o1",
                                submissionOptionId = "o1",
                                result = result,
                            ),
                        ),
                    ),
                    onSelectOption = {},
                    onSubmit = {},
                    onPrevious = {},
                    onNext = {},
                    onRetry = {},
                    onRetryTailLoad = {},
                )
            }
        }

        composeRule.onNodeWithText("正确答案").assertIsDisplayed()
        composeRule.onNodeWithText("你的答案不正确").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("正确选项图标").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("错误选项图标").assertIsDisplayed()
        composeRule.onNodeWithTag("question_option_o2").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正确选项"),
        )
        composeRule.onNodeWithTag("question_option_o1").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已选择，回答错误"),
        )
    }

    @Test
    fun optionContentRemainsVisibleAtLargeFontScale() {
        composeRule.setContent {
            PointQuestTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2.2f)) {
                    QuestionScreen(
                        state = QuestionUiState(
                            mode = PracticeMode.FIRST,
                            loading = false,
                            queue = listOf(queueItem(question)),
                        ),
                        onSelectOption = {},
                        onSubmit = {},
                        onPrevious = {},
                        onNext = {},
                        onRetry = {},
                        onRetryTailLoad = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("这是一个较长的选项内容，用来验证大字体不会被裁切").assertIsDisplayed()
        composeRule.onNodeWithTag("question_option_o1").assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun queueNavigationShowsProgressAndKeepsSubmittedResultVisible() {
        composeRule.setContent {
            PointQuestTheme {
                QuestionScreen(
                    state = QuestionUiState(
                        mode = PracticeMode.FIRST,
                        loading = false,
                        queue = listOf(
                            queueItem(
                                question = question,
                                selectedOptionId = "o1",
                                submissionOptionId = "o1",
                                result = result,
                            ),
                            queueItem(successQuestion("q2"), selectedOptionId = "o2"),
                        ),
                        currentIndex = 0,
                    ),
                    onSelectOption = {},
                    onSubmit = {},
                    onPrevious = {},
                    onNext = {},
                    onRetry = {},
                    onRetryTailLoad = {},
                )
            }
        }

        composeRule.onNodeWithText("第 1 / 2 题").assertIsDisplayed()
        composeRule.onNodeWithText("上一题").assertIsNotEnabled()
        composeRule.onNodeWithText("下一题").assertIsEnabled()
        composeRule.onNodeWithText("你的答案不正确").assertIsDisplayed()
    }

    @Test
    fun secondQuestionEnablesPreviousNavigation() {
        composeRule.setContent {
            PointQuestTheme {
                QuestionScreen(
                    state = QuestionUiState(
                        mode = PracticeMode.FIRST,
                        loading = false,
                        queue = listOf(queueItem(question), queueItem(successQuestion("q2"))),
                        currentIndex = 1,
                    ),
                    onSelectOption = {},
                    onSubmit = {},
                    onPrevious = {},
                    onNext = {},
                    onRetry = {},
                    onRetryTailLoad = {},
                )
            }
        }

        composeRule.onNodeWithText("第 2 / 2 题").assertIsDisplayed()
        composeRule.onNodeWithText("上一题").assertIsEnabled()
    }

    @Test
    fun wrongQuestionsEmptyStateOffersFirstPracticeAndPreviewActions() {
        var firstPracticeOpened = false
        var previewOpened = false
        composeRule.setContent {
            PointQuestTheme {
                WrongQuestionsScreen(
                    state = WrongQuestionsUiState(loading = false),
                    onRetry = {},
                    onLoadMore = {},
                    onSelectQuestion = {},
                    onNoticeShown = {},
                    onFirstPractice = { firstPracticeOpened = true },
                    onPreview = { previewOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("错题本还是空的").assertIsDisplayed()
        composeRule.onNodeWithText("首次答题").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("开始预习").assertIsDisplayed().performClick()
        assertTrue(firstPracticeOpened)
        assertTrue(previewOpened)
    }

    @Test
    fun wrongQuestionsErrorStateKeepsRetryOnly() {
        composeRule.setContent {
            PointQuestTheme {
                WrongQuestionsScreen(
                    state = WrongQuestionsUiState(
                        loading = false,
                        error = UiText.Dynamic("错题加载失败"),
                    ),
                    onRetry = {},
                    onLoadMore = {},
                    onSelectQuestion = {},
                    onNoticeShown = {},
                    onFirstPractice = {},
                    onPreview = {},
                )
            }
        }

        composeRule.onNodeWithText("重试").assertIsDisplayed()
        composeRule.onNodeWithText("首次答题").assertDoesNotExist()
        composeRule.onNodeWithText("开始预习").assertDoesNotExist()
    }

    @Test
    fun firstPracticeCompletedStateOffersPracticeWrongQuestionsAndPreviewActions() {
        var practiceOpened = false
        var wrongQuestionsOpened = false
        var previewOpened = false
        composeRule.setContent {
            PointQuestTheme {
                QuestionScreen(
                    state = QuestionUiState(
                        mode = PracticeMode.FIRST,
                        loading = false,
                        queue = listOf(
                            queueItem(
                                question = question,
                                selectedOptionId = "o2",
                                submissionOptionId = "o2",
                                result = result.copy(correct = true, selectedOptionId = "o2"),
                            ),
                        ),
                        completed = true,
                    ),
                    onSelectOption = {},
                    onSubmit = {},
                    onPrevious = {},
                    onNext = { practiceOpened = true },
                    onRetry = {},
                    onRetryTailLoad = {},
                    onWrongQuestions = { wrongQuestionsOpened = true },
                    onPreview = { previewOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("返回练习").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("错题本").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("开始预习").assertIsDisplayed().performClick()
        assertTrue(practiceOpened)
        assertTrue(wrongQuestionsOpened)
        assertTrue(previewOpened)
    }

    private companion object {
        val question = Question(
            id = "q1",
            stem = "1 + 1 等于多少？",
            basePoints = 5,
            options = listOf(
                QuestionOption("o1", "A", "这是一个较长的选项内容，用来验证大字体不会被裁切", 1),
                QuestionOption("o2", "B", "2", 2),
            ),
        )
        val result = AnswerResult(
            balance = 50,
            correct = false,
            correctOptionId = "o2",
            errorCount = 1,
            explanation = "1 + 1 = 2",
            pointsAwarded = 0,
            selectedOptionId = "o1",
        )

        fun queueItem(
            question: Question,
            selectedOptionId: String? = null,
            submissionOptionId: String? = null,
            result: AnswerResult? = null,
        ) = QuestionQueueItem(
            question = question,
            selectedOptionId = selectedOptionId,
            submissionKey = "key-${question.id}",
            submissionOptionId = submissionOptionId,
            result = result,
            submitError = null,
        )

        fun successQuestion(id: String) = Question(
            id = id,
            stem = "2 + 2 等于多少？",
            basePoints = 5,
            options = listOf(
                QuestionOption("o1", "A", "3", 1),
                QuestionOption("o2", "B", "4", 2),
            ),
        )
    }
}
