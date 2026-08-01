package com.pointquest.android.core.model

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBundleSecurityTest {
    @Test
    fun tokenBundleStringRedactsBothTokens() {
        val accessToken = "access-SENSITIVE-e8bb1a"
        val refreshToken = "refresh-SENSITIVE-a74d29"
        val bundle = TokenBundle(
            accessToken = accessToken,
            accessTokenExpiresAt = Instant.parse("2030-01-01T01:00:00Z"),
            refreshToken = refreshToken,
            refreshTokenExpiresAt = Instant.parse("2030-02-01T00:00:00Z"),
            user = User("student-1", "student", UserRole.STUDENT, 42),
        )

        val rendered = bundle.toString()

        assertFalse(rendered.contains(accessToken))
        assertFalse(rendered.contains(refreshToken))
        assertTrue(rendered.contains("accessToken=[REDACTED]"))
        assertTrue(rendered.contains("refreshToken=[REDACTED]"))
        assertTrue(rendered.contains("username=student"))
    }
}
