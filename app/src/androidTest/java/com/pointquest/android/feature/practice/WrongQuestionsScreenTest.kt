package com.pointquest.android.feature.practice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.ui.theme.PointQuestTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WrongQuestionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedLanguageEmptyStateOffersProfileLanguageAction() {
        var profileOpened = false
        composeRule.setContent {
            PointQuestTheme {
                WrongQuestionsScreen(
                    state = WrongQuestionsUiState(
                        language = LearnerLanguage.FR,
                        loading = false,
                    ),
                    onRetry = {},
                    onLoadMore = {},
                    onSelectQuestion = {},
                    onNoticeShown = {},
                    onFirstPractice = {},
                    onPreview = {},
                    onProfile = { profileOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("调整学习语言").assertIsDisplayed().performClick()

        assertTrue(profileOpened)
    }
}
