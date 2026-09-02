package com.pointquest.android.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PointLedgerType
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.PagedState
import com.pointquest.android.core.ui.theme.PointQuestTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileShowsAccountBalanceAndLedgerOnTheSamePage() {
        composeRule.setContent {
            PointQuestTheme {
                ProfileScreen(
                    state = ProfileUiState(
                        user = User("student-1", "learner", UserRole.STUDENT, 160),
                        paged = PagedState(
                            items = listOf(
                                PointLedgerEntry(
                                    id = "ledger-1",
                                    userId = "student-1",
                                    type = PointLedgerType.ANSWER_REWARD,
                                    delta = 20,
                                    balanceAfter = 160,
                                    answerAttemptId = "attempt-1",
                                    orderId = null,
                                    createdAt = Instant.parse("2026-07-30T08:00:00Z"),
                                ),
                            ),
                            meta = PageMeta(1, 20, 1, 1),
                        ),
                    ),
                    onOrders = {},
                    onPoints = {},
                    onRequestLogout = {},
                    onDismissLogout = {},
                    onConfirmLogout = {},
                    onLanguageChange = {},
                    bottomBar = {},
                )
            }
        }

        composeRule.onNodeWithText("learner").assertIsDisplayed()
        composeRule.onNodeWithText("当前积分：160").assertIsDisplayed()
        composeRule.onNodeWithText("最近流水").assertIsDisplayed()
        composeRule.onNodeWithText("答题奖励").assertIsDisplayed()
        composeRule.onNodeWithText("+20").assertIsDisplayed()
    }
}
