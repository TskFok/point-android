package com.pointquest.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.app.AppRoute
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
        var status by mutableStateOf<SessionStatus>(SessionStatus.SignedOut)
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            AppNavigationTestShell(status, navController)
        }

        composeRule.onNodeWithText("登录功能即将接入").assertIsDisplayed()
        composeRule.runOnUiThread { navController.navigate(AppRoute.Register) }
        composeRule.onNodeWithText("注册功能即将接入").assertIsDisplayed()

        composeRule.runOnUiThread { status = SessionStatus.SignedIn(testStudent()) }
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
