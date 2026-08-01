package com.pointquest.android.core.auth

import com.pointquest.android.core.model.User
import java.time.Instant

data class StoredRefreshSession(
    val refreshToken: String,
    val expiresAt: Instant,
)

data class ActiveSession(
    val user: User,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val generation: Long,
)
