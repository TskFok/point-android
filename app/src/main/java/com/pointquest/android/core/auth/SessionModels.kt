package com.pointquest.android.core.auth

import com.pointquest.android.core.model.User
import java.time.Instant

data class StoredRefreshSession(
    val refreshToken: String,
    val expiresAt: Instant,
) {
    override fun toString(): String =
        "StoredRefreshSession(refreshToken=[REDACTED], expiresAt=$expiresAt)"
}

data class ActiveSession(
    val user: User,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val generation: Long,
) {
    override fun toString(): String =
        "ActiveSession(user=$user, accessToken=[REDACTED], " +
            "accessTokenExpiresAt=$accessTokenExpiresAt, generation=$generation)"
}
