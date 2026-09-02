package com.pointquest.android.feature.shop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.network.PagedState
import com.pointquest.android.core.ui.theme.PointQuestTheme
import com.pointquest.android.data.products.ProductImageUrlFactory
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogEmptyStateUsesShopCopy() {
        composeRule.setContent {
            PointQuestTheme {
                ProductListScreen(
                    state = ProductListUiState(loading = false),
                    imageUrlFactory = ProductImageUrlFactory("https://images.example.test/"),
                    onSearchChange = {},
                    onRetry = {},
                    onRefresh = {},
                    onRefreshErrorShown = {},
                    onLoadMore = {},
                    onProductClick = {},
                )
            }
        }

        composeRule.onNodeWithText("商城正在补充奖励").assertIsDisplayed()
        composeRule.onNodeWithText("暂时没有上架商品，继续积累积分，新的奖励很快就会出现。").assertIsDisplayed()
    }

    @Test
    fun searchEmptyStateKeepsGenericCopy() {
        composeRule.setContent {
            PointQuestTheme {
                ProductListScreen(
                    state = ProductListUiState(search = "贴纸", loading = false),
                    imageUrlFactory = ProductImageUrlFactory("https://images.example.test/"),
                    onSearchChange = {},
                    onRetry = {},
                    onRefresh = {},
                    onRefreshErrorShown = {},
                    onLoadMore = {},
                    onProductClick = {},
                )
            }
        }

        composeRule.onNodeWithText("这里暂时还没有内容").assertIsDisplayed()
    }

    @Test
    fun productCardShowsDescriptionStockAndDeficit() {
        composeRule.setContent {
            PointQuestTheme {
                ProductListScreen(
                    state = ProductListUiState(
                        loading = false,
                        balance = 2,
                        paged = PagedState(
                            items = listOf(product),
                            meta = PageMeta(page = 1, pageSize = 20, total = 1, totalPages = 1),
                        ),
                    ),
                    imageUrlFactory = ProductImageUrlFactory("https://images.example.test/"),
                    onSearchChange = {},
                    onRetry = {},
                    onRefresh = {},
                    onRefreshErrorShown = {},
                    onLoadMore = {},
                    onProductClick = {},
                )
            }
        }

        composeRule.onNodeWithText("当前积分：2").assertIsDisplayed()
        composeRule.onNodeWithText("便携笔记本描述").assertIsDisplayed()
        composeRule.onNodeWithText("库存 3").assertIsDisplayed()
        composeRule.onNodeWithText("还差 8 积分").assertIsDisplayed()
    }

    private companion object {
        val product = Product(
            "p1",
            "成长笔记本",
            "便携笔记本描述",
            "invalid-key",
            10,
            3,
            true,
            Instant.parse("2030-01-01T00:00:00Z"),
            Instant.parse("2030-01-01T00:00:00Z"),
        )
    }
}
