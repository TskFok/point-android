package com.pointquest.android.core.auth

import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionModelsTest {
    @Test
    fun storedRefreshSessionStringRedactsRefreshToken() {
        val token = "refresh-SENSITIVE-7f2c9a"
        val expiresAt = Instant.parse("2030-02-01T00:00:00Z")

        val rendered = StoredRefreshSession(token, expiresAt).toString()

        assertFalse(rendered.contains(token))
        assertTrue(rendered.contains("[REDACTED]"))
        assertTrue(rendered.contains(expiresAt.toString()))
    }

    @Test
    fun activeSessionStringRedactsAccessToken() {
        val token = "access-SENSITIVE-18d4be"
        val session = ActiveSession(
            user = User("student-1", "student", UserRole.STUDENT, 42),
            accessToken = token,
            accessTokenExpiresAt = Instant.parse("2030-01-01T01:00:00Z"),
            generation = 27,
            loginSessionId = 9,
        )

        val rendered = session.toString()

        assertFalse(rendered.contains(token))
        assertTrue(rendered.contains("[REDACTED]"))
        assertTrue(rendered.contains("generation=27"))
        assertTrue(rendered.contains("loginSessionId=9"))
        assertTrue(rendered.contains("username=student"))
    }
}
