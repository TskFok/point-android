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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.R
import com.pointquest.android.core.model.PracticeSummary
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.core.ui.theme.PointQuestTheme
import com.pointquest.android.feature.home.HomeScreen
import com.pointquest.android.feature.home.HomeUiState
import org.junit.Assert.assertTrue
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
    fun loginFormIsDisabledWhileHostApplicationIsInProgress() {
        composeRule.setContent {
            PointQuestTheme {
                LoginScreen(
                    state = AuthUiState(
                        username = "student",
                        password = "secret-password1",
                    ),
                    hostState = RemoteHostUiState(
                        activeHost = "https://api.example.invalid/",
                        draftHost = "https://api.example.invalid/",
                        applying = true,
                    ),
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onLogin = {},
                    onRegister = {},
                )
            }
        }

        composeRule.onNodeWithTag("login_host").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_host_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_username").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_password").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
        composeRule.onNodeWithText("还没有账号？注册账号").assertIsNotEnabled()
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

    @Test
    fun homeCompletedSummaryOffersPreviewAction() {
        var previewOpened = false
        composeRule.setContent {
            PointQuestTheme {
                HomeScreen(
                    state = HomeUiState(
                        username = "student",
                        loading = false,
                        summary = PracticeSummary(
                            activeTotal = 3,
                            balance = 42,
                            firstAnsweredCount = 3,
                            masteredWrongCount = 2,
                            pendingWrongCount = 0,
                            unansweredCount = 0,
                        ),
                    ),
                    onRetry = {},
                    onStartPractice = {},
                    onPreview = { previewOpened = true },
                    onWrongQuestions = {},
                    onOrders = {},
                    onPoints = {},
                    bottomBar = {},
                )
            }
        }

        composeRule.onNodeWithText("暂无待练内容").assertIsDisplayed()
        composeRule.onNodeWithText("开始预习").assertIsDisplayed().performClick()
        assertTrue(previewOpened)
    }

    @Test
    fun homeErrorShowsRetryWithoutPreviewAction() {
        composeRule.setContent {
            PointQuestTheme {
                HomeScreen(
                    state = HomeUiState(
                        username = "student",
                        loading = false,
                        error = UiText.Dynamic("概览加载失败"),
                        canRetry = true,
                    ),
                    onRetry = {},
                    onStartPractice = {},
                    onPreview = {},
                    onWrongQuestions = {},
                    onOrders = {},
                    onPoints = {},
                    bottomBar = {},
                )
            }
        }

        composeRule.onNodeWithText("重试").assertIsDisplayed()
        composeRule.onNodeWithText("开始预习").assertDoesNotExist()
    }
}
