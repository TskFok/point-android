package com.pointquest.android.app

import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class RootDestinationResolverTest {
    private val resolver = RootDestinationResolver()

    @Test
    fun resolvingSessionRoutesNeverShowsMainBeforeRestoreCompletes() {
        assertEquals(AppRoute.Splash, resolver.resolve(SessionStatus.Restoring))
        assertEquals(AppRoute.Login, resolver.resolve(SessionStatus.SignedOut))
        assertEquals(AppRoute.Home, resolver.resolve(SessionStatus.SignedIn(sampleUser())))
    }

    private fun sampleUser() = User(
        id = "student-1",
        username = "student",
        role = UserRole.STUDENT,
        pointsBalance = 42,
    )
}
