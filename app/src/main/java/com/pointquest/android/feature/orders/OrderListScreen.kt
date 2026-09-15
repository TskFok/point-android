package com.pointquest.android.feature.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.pointquest.android.core.ui.components.PointPrimaryButton
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
    onShop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PointScaffold(title = stringResource(R.string.orders_title), modifier = modifier) { padding ->
        val asyncState = when {
            state.loading && state.items.isEmpty() -> AsyncState.Loading
            state.error != null && state.items.isEmpty() -> AsyncState.Error(state.error)
            state.empty -> AsyncState.Empty
            else -> AsyncState.Content(state.items)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.back))
                    }
                    Text(
                        text = stringResource(R.string.paper_commerce_orders_intro),
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (asyncState) {
                is AsyncState.Content -> {
                    items(asyncState.value, key = Order::id) { order ->
                        Column {
                            OrderRow(order, imageUrlFactory) { onOrderClick(order) }
                            HorizontalDivider()
                        }
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
                else -> item {
                    AsyncContent(
                        state = asyncState,
                        onRetry = onRetry,
                        modifier = if (asyncState == AsyncState.Loading) {
                            Modifier.fillMaxWidth().fillParentMaxHeight()
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        emptyContent = { OrdersEmptyState(onShop, Modifier.fillMaxWidth()) },
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun OrdersEmptyState(onShop: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider()
        Text(
            stringResource(R.string.orders_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.orders_empty_copy),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PointPrimaryButton(
            text = stringResource(R.string.orders_empty_shop_action),
            onClick = onShop,
        )
        HorizontalDivider()
    }
}

@Composable
private fun OrderRow(
    order: Order,
    imageUrlFactory: ProductImageUrlFactory,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 116.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("order_${order.id}")
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(width = 72.dp, height = 88.dp),
            shape = RoundedCornerShape(5.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ProductImage(
                    order.productNameSnapshot,
                    order.productImageKeySnapshot,
                    imageUrlFactory,
                    Modifier.fillMaxSize().padding(10.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                order.productNameSnapshot,
                style = MaterialTheme.typography.titleMedium,
            )
            OrderStatusLabel(order.status)
            Text(
                stringResource(R.string.order_points_snapshot, order.pointsCostSnapshot),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.order_number, order.orderNo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.order_created_at, localizedTime(order.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun OrderStatusLabel(status: OrderStatus) {
    val containerColor = when (status) {
        OrderStatus.PENDING_PICKUP -> MaterialTheme.colorScheme.secondaryContainer
        OrderStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
        OrderStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer
        OrderStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (status) {
        OrderStatus.PENDING_PICKUP -> MaterialTheme.colorScheme.onSecondaryContainer
        OrderStatus.COMPLETED -> MaterialTheme.colorScheme.onTertiaryContainer
        OrderStatus.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
        OrderStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = orderStatusText(status),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
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
