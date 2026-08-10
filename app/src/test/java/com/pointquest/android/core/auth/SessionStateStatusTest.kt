package com.pointquest.android.core.auth

import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
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
            loginSessionId = 1,
        )

        assertEquals(SessionStatus.Restoring, state.status.value)

        state.publish(session)
        assertEquals(SessionStatus.SignedIn(sampleUser()), state.status.value)

        state.clear()
        assertEquals(SessionStatus.SignedOut, state.status.value)
    }

    @Test
    fun observerFailureCannotTearActiveStatusOrSkipLaterObservers() {
        val state = SessionState()
        val session = ActiveSession(
            user = sampleUser(),
            accessToken = "access-token",
            accessTokenExpiresAt = Instant.parse("2030-01-01T01:00:00Z"),
            generation = 1,
            loginSessionId = 1,
        )
        val failure = IllegalStateException("observer failed")
        var armed = false
        state.observeActiveSession { if (armed) throw failure }
        var laterObserverValue: ActiveSession? = null
        state.observeActiveSession { laterObserverValue = it }
        armed = true

        try {
            state.publish(session)
            fail("observer failure should be rethrown after publication")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertEquals(session, state.active.value)
        assertEquals(SessionStatus.SignedIn(sampleUser()), state.status.value)
        assertEquals(session, laterObserverValue)

        try {
            state.clear()
            fail("observer failure should be rethrown after clear")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertNull(state.active.value)
        assertEquals(SessionStatus.SignedOut, state.status.value)
        assertNull(laterObserverValue)
    }

    private fun sampleUser() = User(
        id = "student-1",
        username = "student",
        role = UserRole.STUDENT,
        pointsBalance = 42,
    )
}
