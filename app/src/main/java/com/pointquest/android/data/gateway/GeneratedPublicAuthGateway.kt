package com.pointquest.android.data.gateway

import com.pointquest.android.core.model.TokenBundle
import com.pointquest.android.core.model.User
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.toAppResult
import com.pointquest.android.core.network.toDomain
import com.pointquest.android.core.network.toNetworkError
import com.pointquest.android.generated.api.DefaultApi
import com.pointquest.android.generated.model.LoginRequestDto
import com.pointquest.android.generated.model.RefreshRequestDto
import com.pointquest.android.generated.model.RegisterRequestDto
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CancellationException

class GeneratedPublicAuthGateway(
    private val api: DefaultApi,
    private val clock: Clock = Clock.systemUTC(),
) : PublicAuthGateway {
    override suspend fun register(username: String, password: String): AppResult<User> = networkCall {
        api.authRegister(RegisterRequestDto(password = password, username = username))
            .toAppResult { response -> response.user.toDomain() }
    }

    override suspend fun login(username: String, password: String): AppResult<TokenBundle> = networkCall {
        api.authIssueAndroidToken(LoginRequestDto(password = password, username = username))
            .toAppResult { response -> response.toDomain(clock.instant()) }
    }

    override suspend fun refresh(refreshToken: String): AppResult<TokenBundle> = networkCall {
        api.authRefresh(RefreshRequestDto(refreshToken = refreshToken), xCSRFToken = null)
            .toAppResult { response -> response.toDomain(clock.instant()) }
    }

    override suspend fun logout(refreshToken: String): AppResult<Unit> = networkCall {
        api.authLogout(RefreshRequestDto(refreshToken = refreshToken), xCSRFToken = null)
            .toAppResult { Unit }
    }

    private suspend fun <T> networkCall(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: IOException) {
        AppResult.Failure(failure.toNetworkError())
    }
}
