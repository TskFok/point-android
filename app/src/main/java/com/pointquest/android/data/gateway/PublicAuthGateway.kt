package com.pointquest.android.data.gateway

import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.network.AppResult

interface PublicAuthGateway {
    suspend fun register(username: String, password: String): AppResult<User>

    suspend fun login(username: String, password: String): AppResult<TokenBundle>

    suspend fun refresh(refreshToken: String): AppResult<TokenBundle>

    suspend fun logout(refreshToken: String): AppResult<Unit>
}
