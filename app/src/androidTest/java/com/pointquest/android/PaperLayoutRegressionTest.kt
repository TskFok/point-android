package com.pointquest.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.core.ui.theme.PointQuestTheme
import com.pointquest.android.data.products.ProductImageUrlFactory
import com.pointquest.android.feature.shop.ProductListScreen
import com.pointquest.android.feature.shop.ProductListUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaperLayoutRegressionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun shortViewportKeepsLongErrorAndRetryReachableAtLargeFontScale() {
        var retries = 0
        composeRule.setContent {
            val density = LocalDensity.current
            PointQuestTheme {
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.width(360.dp).height(300.dp)) {
                        ProductListScreen(
                            state = ProductListUiState(
                                loading = false,
                                error = UiText.Dynamic("暂时无法连接服务，请确认网络连接后重新尝试加载商品列表。"),
                            ),
                            imageUrlFactory = ProductImageUrlFactory("https://images.example.invalid/"),
                            onSearchChange = {},
                            onRetry = { retries++ },
                            onRefresh = {},
                            onRefreshErrorShown = {},
                            onLoadMore = {},
                            onProductClick = {},
                        )
                    }
                }
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("重试"))
        composeRule.onNodeWithText("重试").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }
}
