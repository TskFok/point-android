package com.pointquest.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.pointquest.android.app.AppNavHost
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.ui.theme.PointQuestTheme

@Composable
internal fun AppNavigationTestShell(
    session: FakeAppSession,
    navController: NavHostController = rememberNavController(),
) {
    PointQuestTheme {
        AppNavHost(
            sessionStatus = session.status,
            navController = navController,
            container = null,
        )
    }
}

internal class FakeAppSession(initialStatus: SessionStatus) {
    var status by mutableStateOf(initialStatus)
        private set

    fun signIn(user: User) {
        status = SessionStatus.SignedIn(user)
    }

    fun expire() {
        status = SessionStatus.SignedOut
    }
}

internal fun testStudent() = User(
    id = "student-device-test",
    username = "student",
    role = UserRole.STUDENT,
    pointsBalance = 42,
)
