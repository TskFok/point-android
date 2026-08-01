package com.pointquest.android.core.auth

import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStateStatusTest {
    @Test
    fun statusStartsRestoringThenFollowsPublishedAndClearedSession() {
        val state = SessionState()
        val session = ActiveSession(
            user = sampleUser(),
            accessToken = "access-token",
            accessTokenExpiresAt = Instant.parse("2030-01-01T01:00:00Z"),
            generation = 1,
        )

        assertEquals(SessionStatus.Restoring, state.status.value)

        state.publish(session)
        assertEquals(SessionStatus.SignedIn(sampleUser()), state.status.value)

        state.clear()
        assertEquals(SessionStatus.SignedOut, state.status.value)
    }

    private fun sampleUser() = User(
        id = "student-1",
        username = "student",
        role = UserRole.STUDENT,
        pointsBalance = 42,
    )
}
