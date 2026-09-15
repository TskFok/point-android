package com.pointquest.android.feature.points

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PointLedgerType
import com.pointquest.android.core.ui.asString
import com.pointquest.android.core.ui.components.AsyncContent
import com.pointquest.android.core.ui.components.AsyncState
import com.pointquest.android.core.ui.components.PagedListFooter
import com.pointquest.android.core.ui.components.PagedListFooterState
import com.pointquest.android.core.ui.components.PointScaffold
import com.pointquest.android.feature.orders.localizedTime

@Composable
fun PointsScreen(
    state: PointsUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PointScaffold(title = stringResource(R.string.points_title), modifier = modifier) { padding ->
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
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.back))
                }
            }
            item {
                PointsHeader(state, onRetry)
            }
            when (asyncState) {
                is AsyncState.Content -> {
                    items(asyncState.value, key = PointLedgerEntry::id) { entry -> PointLedgerRow(entry) }
                    item {
                        when {
                            state.loadingMore -> PagedListFooter(PagedListFooterState.Loading, onLoadMore)
                            state.loadMoreError != null -> PagedListFooter(
                                PagedListFooterState.Error(state.loadMoreError), onLoadMore,
                            )
                            state.canLoadMore -> TextButton(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("points_load_more"),
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
                        emptyContent = { PointsEmptyState(Modifier.fillMaxWidth()) },
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun PointsHeader(state: PointsUiState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.paper_commerce_points_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.points_current_balance),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                state.balance?.toString() ?: stringResource(R.string.points_balance_unknown),
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            )
        }
        HorizontalDivider()
        if (state.error != null && state.items.isNotEmpty()) {
            Text(state.error.asString(), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
        Text(
            stringResource(R.string.paper_commerce_points_records),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun PointsEmptyState(modifier: Modifier) {
    Box(modifier.padding(20.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HorizontalDivider()
            Text(
                stringResource(R.string.empty_state),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
        }
    }
}

@Composable
internal fun PointLedgerRow(entry: PointLedgerEntry) {
    Column(Modifier.fillMaxWidth().heightIn(min = 88.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                pointTypeText(entry.type),
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                deltaText(entry.delta),
                color = when {
                    entry.delta < 0 -> MaterialTheme.colorScheme.error
                    entry.delta > 0 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            )
        }
        Text(
            stringResource(R.string.points_balance_after, entry.balanceAfter),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.points_created_at, localizedTime(entry.createdAt)),
            modifier = Modifier.padding(top = 2.dp, bottom = 13.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
    }
}

@Composable
private fun pointTypeText(type: PointLedgerType): String = stringResource(
    when (type) {
        PointLedgerType.ANSWER_REWARD -> R.string.points_type_answer_reward
        PointLedgerType.ORDER_REDEEM -> R.string.points_type_order_redeem
        PointLedgerType.ORDER_REFUND -> R.string.points_type_order_refund
        PointLedgerType.UNKNOWN -> R.string.points_type_unknown
    },
)

@Composable
private fun deltaText(delta: Int): String = when {
    delta > 0 -> stringResource(R.string.points_delta_positive, delta)
    delta < 0 -> stringResource(R.string.points_delta_negative, delta)
    else -> stringResource(R.string.points_delta_zero)
}
