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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.pointquest.android.data.products.ProductImageUrlFactory

@Composable
fun ProductListScreen(
    state: ProductListUiState,
    imageUrlFactory: ProductImageUrlFactory,
    onSearchChange: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onProductClick: (Product) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    PointScaffold(
        title = stringResource(R.string.shop_title),
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
            val asyncState = when {
                state.loading && state.items.isEmpty() -> AsyncState.Loading
                state.error != null && state.items.isEmpty() -> AsyncState.Error(state.error)
                state.empty -> AsyncState.Empty
                else -> AsyncState.Content(state.items)
            }
            AsyncContent(
                state = asyncState,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            ) { products ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(products, key = Product::id) { product ->
                        ProductRow(product, imageUrlFactory) { onProductClick(product) }
                    }
                    item {
                        when {
                            state.loadingMore -> PagedListFooter(PagedListFooterState.Loading, onLoadMore)
                            state.loadMoreError != null -> PagedListFooter(
                                PagedListFooterState.Error(state.loadMoreError), onLoadMore,
                            )
                            state.canLoadMore -> TextButton(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("product_load_more"),
                            ) { Text(stringResource(R.string.load_more)) }
                            else -> PagedListFooter(PagedListFooterState.End, onLoadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(
    product: Product,
    imageUrlFactory: ProductImageUrlFactory,
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
            Text(stringResource(R.string.product_points_cost, product.pointsCost))
            Text(
                if (!product.isActive) stringResource(R.string.product_status_inactive)
                else if (product.stock <= 0) stringResource(R.string.product_status_out_of_stock)
                else stringResource(R.string.product_stock, product.stock),
            )
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
