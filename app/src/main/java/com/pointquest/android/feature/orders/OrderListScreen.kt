package com.pointquest.android.feature.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.model.Order
import com.pointquest.android.core.model.OrderStatus
import com.pointquest.android.core.ui.components.AsyncContent
import com.pointquest.android.core.ui.components.AsyncState
import com.pointquest.android.core.ui.components.PagedListFooter
import com.pointquest.android.core.ui.components.PagedListFooterState
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointScaffold
import com.pointquest.android.data.products.ProductImageUrlFactory
import com.pointquest.android.feature.shop.ProductImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun OrderListScreen(
    state: OrderListUiState,
    imageUrlFactory: ProductImageUrlFactory,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOrderClick: (Order) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PointScaffold(title = stringResource(R.string.orders_title), modifier = modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.back))
            }
            val asyncState = when {
                state.loading && state.items.isEmpty() -> AsyncState.Loading
                state.error != null && state.items.isEmpty() -> AsyncState.Error(state.error)
                state.empty -> AsyncState.Empty
                else -> AsyncState.Content(state.items)
            }
            AsyncContent(asyncState, onRetry, Modifier.fillMaxSize()) { orders ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(orders, key = Order::id) { order ->
                        OrderRow(order, imageUrlFactory) { onOrderClick(order) }
                    }
                    item {
                        when {
                            state.loadingMore -> PagedListFooter(PagedListFooterState.Loading, onLoadMore)
                            state.loadMoreError != null -> PagedListFooter(
                                PagedListFooterState.Error(state.loadMoreError), onLoadMore,
                            )
                            state.canLoadMore -> TextButton(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("orders_load_more"),
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
private fun OrderRow(
    order: Order,
    imageUrlFactory: ProductImageUrlFactory,
    onClick: () -> Unit,
) {
    PointCard(
        Modifier.fillMaxWidth().heightIn(min = 96.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("order_${order.id}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProductImage(
                order.productNameSnapshot,
                order.productImageKeySnapshot,
                imageUrlFactory,
                Modifier.size(72.dp).align(Alignment.CenterHorizontally),
            )
            Text(order.productNameSnapshot, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.order_number, order.orderNo))
            Text(orderStatusText(order.status))
            Text(stringResource(R.string.order_created_at, localizedTime(order.createdAt)))
        }
    }
}

@Composable
internal fun orderStatusText(status: OrderStatus): String = stringResource(
    when (status) {
        OrderStatus.PENDING_PICKUP -> R.string.order_status_pending_pickup
        OrderStatus.COMPLETED -> R.string.order_status_completed
        OrderStatus.CANCELLED -> R.string.order_status_cancelled
        OrderStatus.UNKNOWN -> R.string.order_status_unknown
    },
)

internal fun localizedTime(instant: Instant): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(instant)
