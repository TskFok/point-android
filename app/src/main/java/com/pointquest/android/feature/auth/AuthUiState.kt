package com.pointquest.android.feature.auth

import com.pointquest.android.core.ui.UiText

data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val usernameError: UiText? = null,
    val passwordError: UiText? = null,
    val confirmPasswordError: UiText? = null,
    val submitting: Boolean = false,
    val message: UiText? = null,
)

sealed interface AuthEvent {
    data class RegistrationSucceeded(val username: String) : AuthEvent
}
