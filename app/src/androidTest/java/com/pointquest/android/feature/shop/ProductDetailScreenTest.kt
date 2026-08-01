package com.pointquest.android.feature.shop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.ui.theme.PointQuestTheme
import com.pointquest.android.data.products.ProductImageUrlFactory
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun redeemUses48DpTargetAndRequiresSecondConfirmation() {
        var confirmation by mutableStateOf(false)
        composeRule.setContent {
            PointQuestTheme {
                ProductDetailScreen(
                    state = ProductDetailUiState(
                        product = product,
                        balance = 20,
                        loading = false,
                        showRedeemConfirmation = confirmation,
                    ),
                    imageUrlFactory = ProductImageUrlFactory("https://images.example.test/"),
                    onRetry = {},
                    onBack = {},
                    onRequestRedeem = { confirmation = true },
                    onDismissRedeem = { confirmation = false },
                    onConfirmRedeem = {},
                    onMessageShown = {},
                )
            }
        }

        composeRule.onNodeWithTag("product_redeem")
            .assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText("确认兑换").assertIsDisplayed()
        composeRule.onNodeWithTag("product_redeem_confirm").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun invalidImageAndRedeemActionRemainReachableAtTwoPointTwoFontScale() {
        composeRule.setContent {
            PointQuestTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2.2f)) {
                    ProductDetailScreen(
                        state = ProductDetailUiState(product = product, balance = 20, loading = false),
                        imageUrlFactory = ProductImageUrlFactory("https://images.example.test/"),
                        onRetry = {},
                        onBack = {},
                        onRequestRedeem = {},
                        onDismissRedeem = {},
                        onConfirmRedeem = {},
                        onMessageShown = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("商品图片占位图").assertIsDisplayed()
        composeRule.onNodeWithText(product.name).assertExists()
        composeRule.onNodeWithText(product.description).assertExists()
        composeRule.onNodeWithTag("product_redeem")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    private companion object {
        val product = Product(
            "p1",
            "成长笔记本",
            "这是一段足够长的商品描述，用来验证大字体模式下内容仍可完整滚动查看。",
            "invalid-key",
            10,
            2,
            true,
            Instant.parse("2030-01-01T00:00:00Z"),
            Instant.parse("2030-01-01T00:00:00Z"),
        )
    }
}
