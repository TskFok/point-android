package com.pointquest.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.app.AppRoute
import com.pointquest.android.core.auth.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unappliedHostPreventsLoginRepositoryCall() {
        val dependencies = FakeAppDependencies()
        composeRule.setContent {
            AppNavigationTestShell(
                session = FakeAppSession(SessionStatus.SignedOut),
                dependencies = dependencies,
            )
        }

        composeRule.onNodeWithTag("login_host").performTextClearance()
        composeRule.onNodeWithTag("login_host").performTextInput("http://new.example.invalid/")
        composeRule.onNodeWithTag("login_username").performTextInput("student")
        composeRule.onNodeWithTag("login_password").performTextInput("Student1234")
        composeRule.onNodeWithTag("login_submit").performClick()

        composeRule.onNodeWithText("请先应用新的服务端地址").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, dependencies.loginCalls) }
    }

    @Test
    fun unappliedHostPreventsNavigationToRegister() {
        val dependencies = FakeAppDependencies()
        composeRule.setContent {
            AppNavigationTestShell(
                session = FakeAppSession(SessionStatus.SignedOut),
                dependencies = dependencies,
            )
        }

        composeRule.onNodeWithTag("login_host").performTextClearance()
        composeRule.onNodeWithTag("login_host").performTextInput("http://new.example.invalid/")
        composeRule.onNodeWithText("还没有账号？注册账号").performClick()

        composeRule.onNodeWithText("欢迎回来").assertIsDisplayed()
        composeRule.onNodeWithText("请先应用新的服务端地址").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, dependencies.registerCalls) }
    }

    @Test
    fun unappliedHostPreventsRegisterRepositoryCallWhenRouteIsOpenedDirectly() {
        val dependencies = FakeAppDependencies()
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            AppNavigationTestShell(
                session = FakeAppSession(SessionStatus.SignedOut),
                navController = navController,
                dependencies = dependencies,
            )
        }

        composeRule.onNodeWithTag("login_host").performTextClearance()
        composeRule.onNodeWithTag("login_host").performTextInput("http://new.example.invalid/")
        composeRule.runOnUiThread { navController.navigate(AppRoute.Register) }
        composeRule.onNodeWithTag("register_username").performTextInput("new_student")
        composeRule.onNodeWithTag("register_password").performTextInput("Student1234")
        composeRule.onNodeWithTag("register_confirm_password").performTextInput("Student1234")
        composeRule.onNodeWithTag("register_submit").performClick()

        composeRule.onNodeWithText("创建学生账号").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, dependencies.registerCalls) }
    }

    @Test
    fun signedOutRegisterLoginAndBottomNavigationFlow() {
        val session = FakeAppSession(SessionStatus.SignedOut)
        val dependencies = FakeAppDependencies(onLogin = session::signIn)
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            AppNavigationTestShell(
                session = session,
                navController = navController,
                dependencies = dependencies,
            )
        }

        composeRule.onNodeWithText("欢迎回来").assertIsDisplayed()
        composeRule.onNodeWithTag("login_host").performTextClearance()
        composeRule.onNodeWithTag("login_host").performTextInput("http://api.example.invalid/")
        composeRule.onNodeWithTag("login_host_apply").performClick()
        composeRule.onNodeWithText("服务端地址已应用").assertIsDisplayed()
        composeRule.onNodeWithText("还没有账号？注册账号").performClick()
        composeRule.onNodeWithText("创建学生账号").assertIsDisplayed()
        composeRule.onNodeWithTag("register_username").performTextInput("new_student")
        composeRule.onNodeWithTag("register_password").performTextInput("Student1234")
        composeRule.onNodeWithTag("register_confirm_password").performTextInput("Student1234")
        composeRule.onNodeWithTag("register_submit").performClick()
        composeRule.onNodeWithText("注册成功，请登录").assertIsDisplayed()
        composeRule.onNodeWithTag("login_username").assertTextContains("new_student")
        composeRule.onNodeWithTag("login_password").performTextInput("Student1234")
        composeRule.onNodeWithTag("login_submit").performClick()
        composeRule.onNodeWithText("练习进度").assertIsDisplayed()

        composeRule.onNodeWithText("练习").performClick()
        composeRule.onNodeWithText("首次答题").assertIsDisplayed()
        composeRule.onNodeWithText("首次答题").performClick()
        composeRule.onNodeWithText("1 + 1 等于几？").assertIsDisplayed()
        composeRule.onNodeWithTag("question_option_option-2").performClick()
        composeRule.onNodeWithTag("question_submit").performClick()
        composeRule.onNodeWithText("回答正确").assertIsDisplayed()

        composeRule.runOnUiThread { navController.navigate(AppRoute.Shop) }
        composeRule.onNodeWithText("测试笔记本").assertIsDisplayed()
        composeRule.onNodeWithTag("product_product-1").performClick()
        composeRule.onNodeWithTag("product_redeem").performClick()
        composeRule.onNodeWithTag("product_redeem_confirm").performClick()
        composeRule.onNodeWithText("订单详情").assertIsDisplayed()
        composeRule.onNodeWithText("订单号：TEST-ORDER-1").assertIsDisplayed()

        composeRule.runOnUiThread { navController.navigate(AppRoute.Shop) }
        composeRule.onNodeWithText("搜索商品").assertIsDisplayed()
        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("学生").assertIsDisplayed()
        composeRule.onNodeWithText("积分明细").performClick()
        composeRule.onNodeWithText("当前积分").assertIsDisplayed()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("练习进度").assertIsDisplayed()
    }

    @Test
    fun practicePreviewHidesBottomNavigationAndReturnsToPracticeHub() {
        val dependencies = FakeAppDependencies()
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            AppNavigationTestShell(
                session = FakeAppSession(SessionStatus.SignedIn(testStudent())),
                navController = navController,
                dependencies = dependencies,
            )
        }

        composeRule.runOnUiThread { navController.navigate(AppRoute.Practice) }
        composeRule.onNodeWithText("开始预习").performClick()
        composeRule.onNodeWithText("预习题数").assertIsDisplayed()
        composeRule.onNodeWithText("首页").assertDoesNotExist()
        composeRule.onNodeWithText("5 题").performClick()
        composeRule.onNodeWithTag("preview_start").performClick()
        composeRule.onNodeWithText("第 1 / 5 题").assertIsDisplayed()

        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.onNodeWithText("首次答题").assertIsDisplayed()
        composeRule.onNodeWithText("错题重练").assertIsDisplayed()
    }

    @Test
    fun completedFirstPracticeWithCurrentQuestionReturnsToPracticeHub() {
        val dependencies = FakeAppDependencies(noUnansweredQuestionsAfterFirstQuestion = true)
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            AppNavigationTestShell(
                session = FakeAppSession(SessionStatus.SignedIn(testStudent())),
                navController = navController,
                dependencies = dependencies,
            )
        }

        composeRule.runOnUiThread { navController.navigate(AppRoute.Practice) }
        composeRule.onNodeWithText("首次答题").performClick()
        composeRule.onNodeWithText("1 + 1 等于几？").assertIsDisplayed()
        composeRule.onNodeWithTag("question_option_option-2").performClick()
        composeRule.onNodeWithTag("question_submit").performClick()
        composeRule.onNodeWithText("回答正确").assertIsDisplayed()
        composeRule.onNodeWithText("下一题").performClick()
        composeRule.onNodeWithText("返回练习").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("首次答题").assertIsDisplayed()
        composeRule.onNodeWithText("返回练习").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<AppRoute.Practice>() == true)
        }
    }
}
