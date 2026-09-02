package com.pointquest.android.data.auth

import com.pointquest.android.core.auth.RefreshCoordinator
import com.pointquest.android.core.auth.SessionManager
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.network.AuthorizedCallExecutor
import com.pointquest.android.core.network.RetryExecutor
import com.pointquest.android.data.gateway.PublicAuthGateway
import com.pointquest.android.data.gateway.StudentGateway
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface AuthRepository {
    suspend fun register(username: String, password: String): AppResult<User>

    suspend fun login(username: String, password: String): AppResult<User>

    suspend fun restore(): AppResult<User>

    suspend fun currentUser(): AppResult<User> =
        error("AuthRepository.currentUser is not implemented.")

    suspend fun logout()
}

class DefaultAuthRepository(
    private val gateway: PublicAuthGateway,
    private val sessionManager: SessionManager,
    private val sessionState: SessionState,
    private val refreshCoordinator: RefreshCoordinator,
    private val studentGateway: StudentGateway? = null,
    private val authorized: AuthorizedCallExecutor? = null,
    private val retry: RetryExecutor? = null,
) : AuthRepository {
    override suspend fun register(username: String, password: String): AppResult<User> =
        gateway.register(username, password)

    override suspend fun login(username: String, password: String): AppResult<User> =
        when (val result = gateway.login(username, password)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                if (result.value.user.role != UserRole.STUDENT) {
                    sessionManager.clear()
                    AppResult.Failure(forbiddenError())
                } else {
                    when (val installed = sessionManager.install(result.value)) {
                        is AppResult.Failure -> installed
                        is AppResult.Success -> AppResult.Success(installed.value.user)
                    }
                }
            }
        }

    override suspend fun restore(): AppResult<User> {
        val observedGeneration = sessionState.active.value?.generation ?: 0L
        return when (val refreshed = refreshCoordinator.refresh(true, observedGeneration)) {
            is AppResult.Failure -> refreshed
            is AppResult.Success -> {
                if (refreshed.value.user.role != UserRole.STUDENT) {
                    sessionManager.clear()
                    AppResult.Failure(forbiddenError())
                } else {
                    AppResult.Success(refreshed.value.user)
                }
            }
        }
    }

    override suspend fun currentUser(): AppResult<User> {
        val studentGateway = studentGateway
        val authorized = authorized
        val retry = retry
        if (studentGateway == null || authorized == null || retry == null) {
            error("AuthRepository.currentUser is not implemented.")
        }
        val result = authorized.executeOperation {
            retry.executeRead { execute { studentGateway.currentUser() } }
        }
        if (result is AppResult.Success && result.value.role == UserRole.STUDENT) {
            sessionManager.replaceUser(result.value)
        }
        return result
    }

    override suspend fun logout() {
        try {
            when (val stored = sessionManager.readStoredRefreshSession()) {
                is AppResult.Failure -> Unit
                is AppResult.Success -> stored.value?.let { gateway.logout(it.refreshToken) }
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    sessionManager.clear()
                } catch (_: Throwable) {
                    // Local state is already cleared before the store clear attempt.
                }
            }
        }
    }

    private fun forbiddenError() = AppError(
        httpStatus = 403,
        code = "FORBIDDEN",
        message = "Student account required",
        requestId = null,
    )
}
