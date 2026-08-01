package com.pointquest.android.feature.auth

import com.pointquest.android.R
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppError
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.auth.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthViewModelTest {
    @Test
    fun invalidCredentialsKeepsUsernameClearsPasswordAndShowsStableFieldError() {
        val repository = FakeAuthRepository(
            loginResult = failure("AUTH_INVALID_CREDENTIALS"),
        )
        val viewModel = viewModel(repository)

        viewModel.updateUsername("student")
        viewModel.updatePassword("wrong-password1")
        viewModel.login()

        assertEquals("student", viewModel.uiState.value.username)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals(UiText.Resource(R.string.auth_error_invalid_credentials), viewModel.uiState.value.passwordError)
        assertEquals(1, repository.loginCalls)
    }

    @Test
    fun registerValidatesExactUsernamePasswordAndConfirmationRulesBeforeCallingRepository() {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)

        viewModel.updateUsername("Ab")
        viewModel.updatePassword("onlyletters")
        viewModel.updateConfirmPassword("different1")
        viewModel.register()

        val state = viewModel.uiState.value
        assertEquals(UiText.Resource(R.string.auth_error_username_format), state.usernameError)
        assertEquals(UiText.Resource(R.string.auth_error_password_format), state.passwordError)
        assertEquals(UiText.Resource(R.string.auth_error_password_mismatch), state.confirmPasswordError)
        assertEquals(0, repository.registerCalls)
    }

    @Test
    fun registerAcceptsBoundaryValuesAndEmitsExactlyOneSuccessEventWithoutCreatingLoginEvent() {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        val username = "a_1"

        viewModel.updateUsername(username)
        viewModel.updatePassword("abcdefghij1")
        viewModel.updateConfirmPassword("abcdefghij1")
        viewModel.register()

        assertEquals(1, repository.registerCalls)
        assertEquals(AuthEvent.RegistrationSucceeded(username), viewModel.events.tryReceive().getOrNull())
        assertNull(viewModel.events.tryReceive().getOrNull())
    }

    @Test
    fun registerRequiresAsciiLetterAndAsciiDigitLikeTheServerContract() {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.updateUsername("student_1")
        viewModel.updatePassword("中文密码中文密码１２3")
        viewModel.updateConfirmPassword("中文密码中文密码１２3")

        viewModel.register()

        assertEquals(UiText.Resource(R.string.auth_error_password_format), viewModel.uiState.value.passwordError)
        assertEquals(0, repository.registerCalls)
    }

    @Test
    fun malformedValidationDetailsNeverCrashAndFallBackToGeneralMessage() {
        val repository = FakeAuthRepository(
            registerResult = AppResult.Failure(
                AppError(
                    httpStatus = 400,
                    code = "VALIDATION_FAILED",
                    message = "bad payload",
                    requestId = "request-1",
                    details = mapOf("fields" to listOf(42, null, mapOf("other" to Any()))),
                ),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.updateUsername("student_1")
        viewModel.updatePassword("password123")
        viewModel.updateConfirmPassword("password123")

        viewModel.register()

        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.confirmPassword)
        assertEquals(UiText.Resource(R.string.auth_error_validation), viewModel.uiState.value.message)
    }

    @Test
    fun serverFieldDetailsMapOnlyKnownFieldsToStableMessages() {
        val repository = FakeAuthRepository(
            registerResult = AppResult.Failure(
                AppError(
                    httpStatus = 400,
                    code = "VALIDATION_FAILED",
                    message = "ignored",
                    requestId = null,
                    details = mapOf(
                        "errors" to listOf(
                            mapOf("field" to "username", "constraints" to listOf("server copy")),
                            mapOf("field" to "password", "constraints" to listOf("server copy")),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.updateUsername("student_1")
        viewModel.updatePassword("password123")
        viewModel.updateConfirmPassword("password123")

        viewModel.register()

        assertEquals(UiText.Resource(R.string.auth_error_username_rejected), viewModel.uiState.value.usernameError)
        assertEquals(UiText.Resource(R.string.auth_error_password_rejected), viewModel.uiState.value.passwordError)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun duplicateSubmitIsIgnoredAndCancellationClearsSecretsWithoutShowingFailure() = runBlocking {
        val deferred = CompletableDeferred<AppResult<User>>()
        val repository = FakeAuthRepository(loginDeferred = deferred)
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val viewModel = AuthViewModel(repository, scope)
        viewModel.updateUsername("student")
        viewModel.updatePassword("password123")

        val first = viewModel.login()
        val duplicate = viewModel.login()

        assertEquals(1, repository.loginCalls)
        assertNull(duplicate)
        assertTrue(viewModel.uiState.value.submitting)
        first!!.cancelAndJoin()
        assertFalse(viewModel.uiState.value.submitting)
        assertEquals("student", viewModel.uiState.value.username)
        assertEquals("", viewModel.uiState.value.password)
        assertNull(viewModel.uiState.value.message)
    }

    private fun viewModel(repository: AuthRepository) =
        AuthViewModel(repository, CoroutineScope(Job() + Dispatchers.Unconfined))

    private class FakeAuthRepository(
        private val loginResult: AppResult<User> = AppResult.Success(student),
        private val registerResult: AppResult<User> = AppResult.Success(student),
        private val loginDeferred: CompletableDeferred<AppResult<User>>? = null,
    ) : AuthRepository {
        var loginCalls = 0
        var registerCalls = 0

        override suspend fun register(username: String, password: String): AppResult<User> {
            registerCalls++
            return registerResult
        }

        override suspend fun login(username: String, password: String): AppResult<User> {
            loginCalls++
            return loginDeferred?.await() ?: loginResult
        }

        override suspend fun restore(): AppResult<User> = error("unused")
        override suspend fun logout() = Unit
    }

    private companion object {
        val student = User("student-1", "student", UserRole.STUDENT, 42)

        fun failure(code: String) = AppResult.Failure(
            AppError(401, code, "server message", null),
        )
    }
}
