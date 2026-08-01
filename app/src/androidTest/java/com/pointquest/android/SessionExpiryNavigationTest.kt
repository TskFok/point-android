package com.pointquest.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.app.AppRoute
import com.pointquest.android.core.auth.SessionStatus
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionExpiryNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun expiryFromDetailClearsProtectedBackStack() {
        var status by mutableStateOf<SessionStatus>(SessionStatus.SignedIn(testStudent()))
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            AppNavigationTestShell(status, navController)
        }

        composeRule.runOnUiThread {
            navController.navigate(AppRoute.Shop)
            navController.navigate(AppRoute.ProductDetail("product-1"))
        }
        composeRule.onNodeWithText("商品详情功能即将接入").assertIsDisplayed()

        composeRule.runOnUiThread { status = SessionStatus.SignedOut }

        composeRule.onNodeWithText("登录功能即将接入").assertIsDisplayed()
        composeRule.onNodeWithText("商品详情功能即将接入").assertDoesNotExist()
        composeRule.runOnIdle { assertFalse(navController.popBackStack()) }
        composeRule.onNodeWithText("商品详情功能即将接入").assertDoesNotExist()
    }
}
