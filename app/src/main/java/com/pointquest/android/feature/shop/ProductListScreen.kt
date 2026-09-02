package com.pointquest.android.feature.shop

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pointquest.android.R
import com.pointquest.android.core.model.Product
import com.pointquest.android.core.ui.components.AsyncContent
import com.pointquest.android.core.ui.components.AsyncState
import com.pointquest.android.core.ui.components.PagedListFooter
import com.pointquest.android.core.ui.components.PagedListFooterState
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointScaffold
import com.pointquest.android.core.ui.asString
import com.pointquest.android.data.products.ProductImageUrlFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    state: ProductListUiState,
    imageUrlFactory: ProductImageUrlFactory,
    onSearchChange: (String) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshErrorShown: () -> Unit,
    onLoadMore: () -> Unit,
    onProductClick: (Product) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshError = state.refreshError?.asString()
    LaunchedEffect(refreshError) {
        if (refreshError != null) {
            snackbarHostState.showSnackbar(refreshError)
            onRefreshErrorShown()
        }
    }
    PointScaffold(
        title = stringResource(R.string.shop_title),
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(min = 56.dp)
                        .testTag("product_search"),
                    label = { Text(stringResource(R.string.product_search_label)) },
                    placeholder = { Text(stringResource(R.string.product_search_hint)) },
                    singleLine = true,
                )
                Text(
                    text = state.balance?.let { stringResource(R.string.product_balance, it) }
                        ?: stringResource(R.string.product_balance_unknown),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("shop_balance"),
                    style = MaterialTheme.typography.titleMedium,
                )
                val asyncState = when {
                    state.loading && state.items.isEmpty() -> AsyncState.Loading
                    state.error != null && state.items.isEmpty() -> AsyncState.Error(state.error)
                    state.empty -> AsyncState.Empty
                    else -> AsyncState.Content(state.items)
                }
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f).testTag("product_pull_refresh"),
                ) {
                    AsyncContent(
                        state = asyncState,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                        emptyContent = {
                            if (state.search.isBlank()) {
                                ShopEmptyState(Modifier.fillMaxSize())
                            } else {
                                PointCard(Modifier.fillMaxSize().padding(16.dp)) {
                                    Text(
                                        stringResource(R.string.empty_state),
                                        modifier = Modifier.padding(20.dp),
                                    )
                                }
                            }
                        },
                    ) { products ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(products, key = Product::id) { product ->
                                ProductRow(
                                    product = product,
                                    imageUrlFactory = imageUrlFactory,
                                    deficit = state.pointsDeficit(product),
                                ) { onProductClick(product) }
                            }
                            item {
                                when {
                                    state.loadingMore -> PagedListFooter(PagedListFooterState.Loading, onLoadMore)
                                    state.loadMoreError != null -> PagedListFooter(
                                        PagedListFooterState.Error(state.loadMoreError), onLoadMore,
                                    )
                                    state.canLoadMore -> TextButton(
                                        onClick = onLoadMore,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("product_load_more"),
                                    ) { Text(stringResource(R.string.load_more)) }
                                    else -> PagedListFooter(PagedListFooterState.End, onLoadMore)
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ShopEmptyState(modifier: Modifier) {
    Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        PointCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.shop_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.shop_empty_copy),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProductRow(
    product: Product,
    imageUrlFactory: ProductImageUrlFactory,
    deficit: Int?,
    onClick: () -> Unit,
) {
    PointCard(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("product_${product.id}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProductImage(product, imageUrlFactory, Modifier.size(88.dp).align(Alignment.CenterHorizontally))
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text(product.description, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.product_points_cost, product.pointsCost))
            Text(
                when {
                    !product.isActive -> stringResource(R.string.product_status_inactive)
                    product.stock <= 0 -> stringResource(R.string.product_sold_out)
                    else -> stringResource(R.string.product_stock_count, product.stock)
                },
            )
            if (deficit != null) {
                Text(
                    stringResource(R.string.product_deficit, deficit),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("product_deficit_${product.id}"),
                )
            }
        }
    }
}

@Composable
internal fun ProductImage(
    product: Product,
    imageUrlFactory: ProductImageUrlFactory,
    modifier: Modifier = Modifier,
) = ProductImage(product.name, product.imageKey, imageUrlFactory, modifier)

@Composable
internal fun ProductImage(
    name: String,
    imageKey: String,
    imageUrlFactory: ProductImageUrlFactory,
    modifier: Modifier = Modifier,
) {
    val placeholder: Painter = painterResource(R.drawable.ic_product_placeholder)
    val url = imageUrlFactory.urlOrNull(imageKey)
    if (url == null) {
        Image(
            painter = placeholder,
            contentDescription = stringResource(R.string.product_image_placeholder),
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = stringResource(R.string.product_image_description, name),
            modifier = modifier,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
        )
    }
}
