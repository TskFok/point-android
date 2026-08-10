package com.pointquest.android.app

import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationPolicyTest {
    @Test
    fun authenticationTransitionsReplaceTheEntireRootBackStack() {
        val signedOut = AppNavigationPolicy.rootRequest(
            SessionStatus.SignedOut,
            alreadyAtTarget = false,
        )
        val signedIn = AppNavigationPolicy.rootRequest(
            SessionStatus.SignedIn(sampleUser()),
            alreadyAtTarget = false,
        )

        assertEquals(AppRoute.Login, signedOut?.target)
        assertTrue(signedOut?.clearBackStack == true)
        assertTrue(signedOut?.launchSingleTop == true)
        assertEquals(AppRoute.Home, signedIn?.target)
        assertTrue(signedIn?.clearBackStack == true)
        assertTrue(signedIn?.launchSingleTop == true)
        assertNull(AppNavigationPolicy.rootRequest(SessionStatus.SignedOut, alreadyAtTarget = true))
    }

    @Test
    fun everyTopLevelTabUsesHomeAnchorAndStateRestorationOptions() {
        val routes = listOf(AppRoute.Home, AppRoute.Practice, AppRoute.Shop, AppRoute.Profile)

        routes.forEach { route ->
            val request = AppNavigationPolicy.topLevelRequest(route)

            assertEquals(route, request.target)
            assertEquals(AppRoute.Home, request.anchor)
            assertTrue(request.launchSingleTop)
            assertTrue(request.saveState)
            assertTrue(request.restoreState)
        }
    }

    @Test
    fun bottomBarIsHiddenFromAuthenticationAndDetailRoutes() {
        val topLevel = listOf(AppRoute.Home, AppRoute.Practice, AppRoute.Shop, AppRoute.Profile)
        val withoutBottomBar = listOf(
            AppRoute.Splash,
            AppRoute.Login,
            AppRoute.Register,
            AppRoute.Preview,
            AppRoute.Question(PracticeMode.FIRST, "question-1"),
            AppRoute.WrongQuestions,
            AppRoute.ProductDetail("product-1"),
            AppRoute.Orders,
            AppRoute.OrderDetail("order-1"),
            AppRoute.Points,
        )

        topLevel.forEach { assertTrue(AppNavigationPolicy.showsBottomBar(it)) }
        withoutBottomBar.forEach { assertFalse(AppNavigationPolicy.showsBottomBar(it)) }
    }

    private fun sampleUser() = User(
        id = "student-1",
        username = "student",
        role = UserRole.STUDENT,
        pointsBalance = 42,
    )
}
