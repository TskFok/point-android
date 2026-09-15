package com.pointquest.android.feature.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.model.OrderStatus
import com.pointquest.android.core.ui.components.AsyncContent
import com.pointquest.android.core.ui.components.AsyncState
import com.pointquest.android.core.ui.components.PointScaffold
import com.pointquest.android.data.products.ProductImageUrlFactory
import com.pointquest.android.feature.shop.ProductImage

@Composable
fun OrderDetailScreen(
    state: OrderDetailUiState,
    imageUrlFactory: ProductImageUrlFactory,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PointScaffold(title = stringResource(R.string.order_detail_title), modifier = modifier) { padding ->
        val asyncState = when {
            state.loading && state.order == null -> AsyncState.Loading
            state.error != null && state.order == null -> AsyncState.Error(state.error)
            state.order != null -> AsyncState.Content(state.order)
            else -> AsyncState.Empty
        }
        AsyncContent(asyncState, onRetry, Modifier.fillMaxSize().padding(padding)) { order ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.back))
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ProductImage(
                            order.productNameSnapshot,
                            order.productImageKeySnapshot,
                            imageUrlFactory,
                            Modifier.size(160.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(order.productNameSnapshot, style = MaterialTheme.typography.headlineSmall)
                    OrderStatusLabel(order.status)
                }
                Text(
                    stringResource(R.string.paper_commerce_order_information),
                    style = MaterialTheme.typography.titleLarge,
                )
                Column {
                    OrderDetailLine(stringResource(R.string.order_number, order.orderNo))
                    OrderDetailLine(stringResource(R.string.order_status, orderStatusText(order.status)))
                    OrderDetailLine(stringResource(R.string.order_points_snapshot, order.pointsCostSnapshot))
                    OrderDetailLine(stringResource(R.string.order_balance_after, order.balance))
                    OrderDetailLine(stringResource(R.string.order_created_at, localizedTime(order.createdAt)))
                    when (order.status) {
                        OrderStatus.COMPLETED -> order.completedAt?.let {
                            OrderDetailLine(stringResource(R.string.order_completed_at, localizedTime(it)))
                        }
                        OrderStatus.CANCELLED -> order.cancelledAt?.let {
                            OrderDetailLine(stringResource(R.string.order_cancelled_at, localizedTime(it)))
                        }
                        OrderStatus.PENDING_PICKUP, OrderStatus.UNKNOWN -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderDetailLine(text: String) {
    HorizontalDivider()
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}
