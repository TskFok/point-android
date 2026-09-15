package com.pointquest.android

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.app.AppDependencies
import com.pointquest.android.app.AppNavHost
import com.pointquest.android.app.AppRoute
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.theme.PointQuestTheme
import com.pointquest.android.data.auth.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionUpdateNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun answeringAfterVisitingProfileKeepsResultAndAllowsNextQuestionWhenBalanceRefreshes() {
        val base = FakeAppDependencies()
        val dependencies = object : AppDependencies by base {
            override val authRepository = object : AuthRepository by base.authRepository {
                override suspend fun currentUser() = AppResult.Success(
                    checkNotNull(base.sessionState.active.value).let { active ->
                        val user = active.user.copy(
                            pointsBalance = base.appDataSync.state.value.balance ?: active.user.pointsBalance,
                        )
                        base.sessionState.publish(active.copy(user = user))
                        user
                    },
                )
            }
        }
        lateinit var navController: NavHostController
        composeRule.setContent {
            val sessionStatus by dependencies.sessionState.status.collectAsStateWithLifecycle()
            navController = rememberNavController()
            PointQuestTheme {
                AppNavHost(sessionStatus = sessionStatus, navController = navController, container = dependencies)
            }
        }

        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("学生").assertIsDisplayed()
        composeRule.onNodeWithText("练习").performClick()
        composeRule.onNodeWithText("首次答题").performClick()
        composeRule.onNodeWithTag("question_option_option-2").performClick()
        composeRule.onNodeWithTag("question_submit").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            (dependencies.sessionState.status.value as? SessionStatus.SignedIn)?.user?.pointsBalance == 47
        }
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<AppRoute.Question>() == true)
        }
        composeRule.onNodeWithText("回答正确").assertIsDisplayed()
        composeRule.onNodeWithText("下一题").performClick()
        composeRule.onNodeWithText("第 2 / 2 题").assertIsDisplayed()
        composeRule.onNodeWithText("上一题").performClick()
        composeRule.onNodeWithText("回答正确").assertIsDisplayed()

        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.onNodeWithText("首次答题").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<AppRoute.Practice>() == true)
        }
    }

    @Test
    fun switchingUserStillReplacesProtectedBackStack() {
        val session = FakeAppSession(SessionStatus.SignedIn(testStudent()))
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            AppNavigationTestShell(session, navController)
        }
        composeRule.runOnUiThread {
            navController.navigate(AppRoute.ProductDetail("product-1"))
        }
        composeRule.onNodeWithText("真实商品详情分支").assertIsDisplayed()

        composeRule.runOnUiThread {
            session.signIn(testStudent().copy(id = "another-student"))
        }

        composeRule.onNodeWithText("练习进度").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<AppRoute.Home>() == true)
            assertEquals(null, navController.previousBackStackEntry)
        }
    }
}
