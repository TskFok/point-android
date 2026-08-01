package com.pointquest.android.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.ui.theme.PointQuestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rootContentFollowsRestoreAndAuthenticationStatus() {
        var status by mutableStateOf<SessionStatus>(SessionStatus.Restoring)
        composeRule.setContent {
            PointQuestTheme { AppNavHost(status) }
        }

        composeRule.onNodeWithText("正在恢复登录状态").assertIsDisplayed()

        composeRule.runOnUiThread { status = SessionStatus.SignedOut }
        composeRule.onNodeWithText("登录功能即将接入").assertIsDisplayed()

        composeRule.runOnUiThread { status = SessionStatus.SignedIn(sampleUser()) }
        composeRule.onNodeWithText("首页功能即将接入").assertIsDisplayed()
    }

    @Test
    fun fourTopLevelTabsAreReachableFromTheBottomBar() {
        composeRule.setContent {
            PointQuestTheme { AppNavHost(SessionStatus.SignedIn(sampleUser())) }
        }

        composeRule.onNodeWithText("练习").performClick()
        composeRule.onNodeWithText("练习功能即将接入").assertIsDisplayed()

        composeRule.onNodeWithText("商店").performClick()
        composeRule.onNodeWithText("商店功能即将接入").assertIsDisplayed()

        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("个人中心功能即将接入").assertIsDisplayed()

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("首页功能即将接入").assertIsDisplayed()
    }

    private fun sampleUser() = User(
        id = "student-1",
        username = "student",
        role = UserRole.STUDENT,
        pointsBalance = 42,
    )
}
