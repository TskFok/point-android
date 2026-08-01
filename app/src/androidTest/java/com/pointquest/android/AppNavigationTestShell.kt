package com.pointquest.android

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.pointquest.android.app.AppNavHost
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.ui.theme.PointQuestTheme

@Composable
internal fun AppNavigationTestShell(
    sessionStatus: SessionStatus,
    navController: NavHostController = rememberNavController(),
) {
    PointQuestTheme {
        AppNavHost(
            sessionStatus = sessionStatus,
            navController = navController,
            container = null,
        )
    }
}

internal fun testStudent() = User(
    id = "student-device-test",
    username = "student",
    role = UserRole.STUDENT,
    pointsBalance = 42,
)
