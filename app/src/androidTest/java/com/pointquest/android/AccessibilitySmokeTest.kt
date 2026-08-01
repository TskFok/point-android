package com.pointquest.android

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.core.ui.theme.PointQuestTheme
import com.pointquest.android.feature.auth.AuthMessageTone
import com.pointquest.android.feature.auth.AuthUiState
import com.pointquest.android.feature.auth.LoginScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilitySmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onePointFiveFontScaleKeepsPrimaryActionReachableAndErrorTextSemantic() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                PointQuestTheme {
                    LoginScreen(
                        state = AuthUiState(
                            message = UiText.Resource(R.string.auth_error_invalid_credentials),
                            messageTone = AuthMessageTone.Error,
                        ),
                        onUsernameChange = {},
                        onPasswordChange = {},
                        onLogin = {},
                        onRegister = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("login_submit")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
        composeRule.onNodeWithText("用户名或密码不正确")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }

    @Test
    fun bottomNavigationIconsHaveDescriptionsAtOnePointFiveFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                AppNavigationTestShell(SessionStatus.SignedIn(testStudent()))
            }
        }

        listOf("首页", "练习", "商店", "我的").forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
    }
}
