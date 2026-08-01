package com.pointquest.android.core.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.core.ui.components.AsyncContent
import com.pointquest.android.core.ui.components.AsyncState
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointPrimaryButton
import com.pointquest.android.core.ui.theme.PointQuestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryButtonMeetsMaterialTouchTarget() {
        composeRule.setContent {
            PointQuestTheme {
                PointPrimaryButton(
                    text = "继续",
                    modifier = Modifier.testTag("primary-button"),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("primary-button").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun errorVisualHasAReadableContentDescription() {
        composeRule.setContent {
            PointQuestTheme {
                AsyncContent<String>(
                    state = AsyncState.Error(UiText.Dynamic("服务器开小差了")),
                    onRetry = {},
                ) { value -> Text(value) }
            }
        }

        composeRule.onNodeWithContentDescription("加载失败").assertIsDisplayed()
    }

    @Test
    fun cardGrowsInsteadOfClippingAtLargeFontScale() {
        val copy = "课堂任务已经准备好，请先阅读题目，再选择你认为正确的答案。"
        composeRule.setContent {
            PointQuestTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2.2f)) {
                    PointCard(
                        modifier = Modifier
                            .width(240.dp)
                            .testTag("large-text-card"),
                    ) {
                        Text(copy)
                    }
                }
            }
        }

        composeRule.onNodeWithText(copy).assertIsDisplayed()
        composeRule.onNodeWithTag("large-text-card").assertHeightIsAtLeast(96.dp)
    }
}
