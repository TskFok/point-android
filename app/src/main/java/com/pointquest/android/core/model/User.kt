package com.pointquest.android.core.model

import java.time.Instant

data class User(
    val id: String,
    val username: String,
    val role: UserRole,
    val pointsBalance: Int,
)

enum class UserRole {
    ADMIN,
    STUDENT,
    UNKNOWN,
}

data class TokenBundle(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val user: User,
)
