package com.pointquest.android.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.ui.theme.PointQuestTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        composeRule.onNodeWithText("欢迎回来").assertIsDisplayed()

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

    @Test
    fun authenticationAndInvalidationRemovePreviousBackStackEntries() {
        var status by mutableStateOf<SessionStatus>(SessionStatus.SignedOut)
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            PointQuestTheme { AppNavHost(status, navController = navController) }
        }

        composeRule.onNodeWithText("还没有账号？注册账号").performClick()
        composeRule.onNodeWithText("创建学生账号").assertIsDisplayed()
        composeRule.runOnUiThread { status = SessionStatus.SignedIn(sampleUser()) }
        composeRule.onNodeWithText("首页功能即将接入").assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(navController.popBackStack()) }

        composeRule.runOnUiThread { navController.navigate(AppRoute.Practice) }
        composeRule.onNodeWithText("练习功能即将接入").assertIsDisplayed()
        composeRule.runOnUiThread { status = SessionStatus.SignedOut }
        composeRule.onNodeWithText("欢迎回来").assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(navController.popBackStack()) }
    }

    @Test
    fun topLevelTabsUseSingleTopAndRestoreSavedEntryState() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            PointQuestTheme {
                AppNavHost(SessionStatus.SignedIn(sampleUser()), navController = navController)
            }
        }

        composeRule.runOnIdle {
            navController.currentBackStackEntry?.savedStateHandle?.set("tab-marker", "home")
        }
        composeRule.onNodeWithText("练习").performClick()
        composeRule.runOnIdle {
            navController.currentBackStackEntry?.savedStateHandle?.set("tab-marker", "practice")
        }

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("首页功能即将接入").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("home", navController.currentBackStackEntry?.savedStateHandle?.get("tab-marker"))
        }
        val stackSize = navController.currentBackStack.value.size
        composeRule.onNodeWithText("首页").performClick()
        composeRule.runOnIdle { assertEquals(stackSize, navController.currentBackStack.value.size) }

        composeRule.onNodeWithText("练习").performClick()
        composeRule.onNodeWithText("练习功能即将接入").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("practice", navController.currentBackStackEntry?.savedStateHandle?.get("tab-marker"))
        }
    }

    @Test
    fun detailRouteDoesNotRenderTheTopLevelBottomBar() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            PointQuestTheme {
                AppNavHost(SessionStatus.SignedIn(sampleUser()), navController = navController)
            }
        }

        composeRule.runOnUiThread { navController.navigate(AppRoute.ProductDetail("product-1")) }
        composeRule.onNodeWithText("商品详情功能即将接入").assertIsDisplayed()
        composeRule.onNodeWithText("首页").assertDoesNotExist()
        composeRule.onNodeWithText("练习").assertDoesNotExist()
        composeRule.onNodeWithText("商店").assertDoesNotExist()
        composeRule.onNodeWithText("我的").assertDoesNotExist()
    }

    private fun sampleUser() = User(
        id = "student-1",
        username = "student",
        role = UserRole.STUDENT,
        pointsBalance = 42,
    )
}
