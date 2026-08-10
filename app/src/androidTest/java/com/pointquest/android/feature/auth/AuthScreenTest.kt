package com.pointquest.android.feature.auth

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.R
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.core.ui.theme.PointQuestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginShowsReadableErrorAndAccessibleDisabledSubmitTarget() {
        composeRule.setContent {
            PointQuestTheme {
                LoginScreen(
                    state = AuthUiState(
                        username = "student",
                        password = "secret-password1",
                        passwordError = UiText.Resource(R.string.auth_error_invalid_credentials),
                        submitting = true,
                    ),
                    hostState = RemoteHostUiState(
                        activeHost = "https://api.example.invalid/",
                        draftHost = "https://api.example.invalid/",
                    ),
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onLogin = {},
                    onRegister = {},
                    onHostChange = {},
                    onApplyHost = {},
                )
            }
        }

        composeRule.onNodeWithTag("login_host").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag("login_host_apply")
            .assertHeightIsAtLeast(48.dp)
            .assertIsNotEnabled()
        composeRule.onNodeWithText("用户名或密码不正确").assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit")
            .assertHeightIsAtLeast(48.dp)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("login_password").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Password, Unit),
        )
        composeRule.onNodeWithText("secret-password1").assertDoesNotExist()
    }

    @Test
    fun registerPasswordFieldsUsePasswordSemanticsAndPrimaryTargetIsLargeEnough() {
        composeRule.setContent {
            PointQuestTheme {
                RegisterScreen(
                    state = AuthUiState(),
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onConfirmPasswordChange = {},
                    onRegister = {},
                    onBackToLogin = {},
                )
            }
        }

        composeRule.onNodeWithTag("register_password").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Password, Unit),
        )
        composeRule.onNodeWithTag("register_confirm_password").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Password, Unit),
        )
        composeRule.onNodeWithTag("register_submit").assertHeightIsAtLeast(48.dp)
    }
}
