package com.pointquest.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.core.auth.SessionStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signedOutRegisterLoginAndBottomNavigationFlow() {
        val session = FakeAppSession(SessionStatus.SignedOut)
        composeRule.setContent {
            AppNavigationTestShell(session)
        }

        composeRule.onNodeWithText("欢迎回来").assertIsDisplayed()
        composeRule.onNodeWithText("还没有账号？注册账号").performClick()
        composeRule.onNodeWithText("创建学生账号").assertIsDisplayed()

        composeRule.runOnUiThread { session.signIn(testStudent()) }
        composeRule.onNodeWithText("首页功能即将接入").assertIsDisplayed()

        composeRule.onNodeWithText("练习").performClick()
        composeRule.onNodeWithText("练习功能即将接入").assertIsDisplayed()
        composeRule.onNodeWithText("商店").performClick()
        composeRule.onNodeWithText("商店功能即将接入").assertIsDisplayed()
        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("个人中心功能即将接入").assertIsDisplayed()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("首页功能即将接入").assertIsDisplayed()
    }
}
