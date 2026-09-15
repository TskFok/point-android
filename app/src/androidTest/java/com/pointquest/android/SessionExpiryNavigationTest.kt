package com.pointquest.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.app.AppRoute
import com.pointquest.android.core.auth.SessionStatus
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionExpiryNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun expiryFromDetailClearsProtectedBackStack() {
        val session = FakeAppSession(SessionStatus.SignedIn(testStudent()))
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            AppNavigationTestShell(session, navController)
        }

        composeRule.runOnUiThread {
            navController.navigate(AppRoute.Shop)
            navController.navigate(AppRoute.ProductDetail("product-1"))
        }
        composeRule.onNodeWithText("真实商品详情分支").assertIsDisplayed()

        composeRule.runOnUiThread { session.expire() }

        composeRule.onNodeWithText("欢迎回来").assertIsDisplayed()
        composeRule.onNodeWithText("真实商品详情分支").assertDoesNotExist()

        val activity = composeRule.activity
        composeRule.runOnUiThread {
            assertTrue(navController.currentDestination?.hasRoute<AppRoute.Login>() == true)
            assertNull(navController.previousBackStackEntry)
            activity.onBackPressedDispatcher.onBackPressed()
            // Root Back may background the task on Android 12+ instead of finishing it.
            assertTrue(
                navController.currentDestination == null ||
                    navController.currentDestination?.hasRoute<AppRoute.Login>() == true,
            )
            assertNull(navController.previousBackStackEntry)
        }
        composeRule.onNodeWithText("真实商品详情分支").assertDoesNotExist()
    }
}
