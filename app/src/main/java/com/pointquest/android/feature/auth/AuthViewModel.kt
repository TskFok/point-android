package com.pointquest.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiErrorMapper
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.auth.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val scopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = kotlinx.coroutines.flow.MutableStateFlow(AuthUiState())
    private val eventChannel = Channel<AuthEvent>(Channel.BUFFERED)

    val uiState: kotlinx.coroutines.flow.StateFlow<AuthUiState> = mutableUiState
    val events: ReceiveChannel<AuthEvent> = eventChannel

    fun updateUsername(value: String) {
        if (mutableUiState.value.submitting) return
        mutableUiState.value = mutableUiState.value.copy(
            username = value,
            usernameError = null,
            message = null,
            messageTone = null,
        )
    }

    fun updatePassword(value: String) {
        if (mutableUiState.value.submitting) return
        mutableUiState.value = mutableUiState.value.copy(
            password = value,
            passwordError = null,
            message = null,
            messageTone = null,
        )
    }

    fun updateConfirmPassword(value: String) {
        if (mutableUiState.value.submitting) return
        mutableUiState.value = mutableUiState.value.copy(
            confirmPassword = value,
            confirmPasswordError = null,
            message = null,
            messageTone = null,
        )
    }

    fun prefillUsername(username: String, registrationSucceeded: Boolean = false) {
        if (mutableUiState.value.submitting) return
        mutableUiState.value = mutableUiState.value.copy(
            username = username,
            message = if (registrationSucceeded) {
                UiText.Resource(R.string.auth_registration_succeeded)
            } else {
                null
            },
            messageTone = if (registrationSucceeded) AuthMessageTone.Success else null,
        )
    }

    fun login(): Job? {
        val current = mutableUiState.value
        if (current.submitting) return null
        val usernameError = current.username.takeIf(String::isBlank)?.let {
            UiText.Resource(R.string.auth_error_username_required)
        }
        val passwordError = current.password.takeIf(String::isBlank)?.let {
            UiText.Resource(R.string.auth_error_password_required)
        }
        if (usernameError != null || passwordError != null) {
            mutableUiState.value = current.copy(
                usernameError = usernameError,
                passwordError = passwordError,
            )
            return null
        }

        mutableUiState.value = current.copy(
            submitting = true,
            usernameError = null,
            passwordError = null,
            message = null,
            messageTone = null,
        )
        return scope.launch {
            try {
                when (val result = authRepository.login(current.username, current.password)) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> applyLoginError(result.error)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                mutableUiState.value = mutableUiState.value.copy(
                    password = "",
                    submitting = false,
                )
            }
        }
    }

    fun register(): Job? {
        val current = mutableUiState.value
        if (current.submitting) return null
        val usernameError = if (USERNAME_PATTERN.matches(current.username)) {
            null
        } else {
            UiText.Resource(R.string.auth_error_username_format)
        }
        val passwordError = if (isValidRegistrationPassword(current.password)) {
            null
        } else {
            UiText.Resource(R.string.auth_error_password_format)
        }
        val confirmPasswordError = if (current.confirmPassword == current.password) {
            null
        } else {
            UiText.Resource(R.string.auth_error_password_mismatch)
        }
        if (usernameError != null || passwordError != null || confirmPasswordError != null) {
            mutableUiState.value = current.copy(
                usernameError = usernameError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError,
            )
            return null
        }

        mutableUiState.value = current.copy(
            submitting = true,
            usernameError = null,
            passwordError = null,
            confirmPasswordError = null,
            message = null,
            messageTone = null,
        )
        return scope.launch {
            try {
                when (val result = authRepository.register(current.username, current.password)) {
                    is AppResult.Success -> eventChannel.send(AuthEvent.RegistrationSucceeded(current.username))
                    is AppResult.Failure -> applyRegisterError(result.error)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                mutableUiState.value = mutableUiState.value.copy(
                    password = "",
                    confirmPassword = "",
                    submitting = false,
                )
            }
        }
    }

    private fun applyLoginError(error: AppError) {
        mutableUiState.value = when (error.code) {
            "AUTH_INVALID_CREDENTIALS" -> mutableUiState.value.copy(
                passwordError = UiText.Resource(R.string.auth_error_invalid_credentials),
            )
            "VALIDATION_FAILED" -> mutableUiState.value.withValidationDetails(error)
            else -> mutableUiState.value.copy(
                message = UiErrorMapper.map(error),
                messageTone = AuthMessageTone.Error,
            )
        }
    }

    private fun applyRegisterError(error: AppError) {
        mutableUiState.value = when (error.code) {
            "VALIDATION_FAILED" -> mutableUiState.value.withValidationDetails(error)
            "USERNAME_ALREADY_EXISTS", "CONFLICT" -> mutableUiState.value.copy(
                usernameError = UiText.Resource(R.string.auth_error_username_exists),
            )
            else -> mutableUiState.value.copy(
                message = UiErrorMapper.map(error),
                messageTone = AuthMessageTone.Error,
            )
        }
    }

    private fun AuthUiState.withValidationDetails(error: AppError): AuthUiState {
        val rejectedFields = validationFields(error.details)
        val usernameRejected = "username" in rejectedFields
        val passwordRejected = "password" in rejectedFields
        val confirmPasswordRejected = "confirmPassword" in rejectedFields
        return copy(
            usernameError = if (usernameRejected) UiText.Resource(R.string.auth_error_username_rejected) else null,
            passwordError = if (passwordRejected) UiText.Resource(R.string.auth_error_password_rejected) else null,
            confirmPasswordError = if (confirmPasswordRejected) {
                UiText.Resource(R.string.auth_error_confirm_password_rejected)
            } else {
                null
            },
            message = if (!usernameRejected && !passwordRejected && !confirmPasswordRejected) {
                UiText.Resource(R.string.auth_error_validation)
            } else {
                null
            },
            messageTone = AuthMessageTone.Error,
        )
    }

    private fun validationFields(details: Map<String, Any?>): Set<String> =
        (details["errors"] as? List<*>)
            ?.mapNotNull { (it as? Map<*, *>)?.get("field") as? String }
            ?.filterTo(mutableSetOf()) { it in VALIDATION_FIELDS }
            .orEmpty()

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private companion object {
        val USERNAME_PATTERN = Regex("^[a-z0-9_]{3,32}$")
        val PASSWORD_PATTERN = Regex("^(?=.*[A-Za-z])(?=.*[0-9]).{10,}$")
        val VALIDATION_FIELDS = setOf("username", "password", "confirmPassword")

        fun isValidRegistrationPassword(password: String): Boolean = PASSWORD_PATTERN.matches(password)
    }
}
