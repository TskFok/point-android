package com.pointquest.android.feature.points

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.pointquest.android.core.ui.components.PointCard
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.back))
            }
            PointCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.points_current_balance), style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.balance?.toString() ?: stringResource(R.string.points_balance_unknown),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (state.error != null && state.items.isNotEmpty()) {
                        Text(state.error.asString(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            val asyncState = when {
                state.loading && state.items.isEmpty() -> AsyncState.Loading
                state.error != null && state.items.isEmpty() -> AsyncState.Error(state.error)
                state.empty -> AsyncState.Empty
                else -> AsyncState.Content(state.items)
            }
            AsyncContent(asyncState, onRetry, Modifier.fillMaxSize()) { entries ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(entries, key = PointLedgerEntry::id) { entry -> PointLedgerRow(entry) }
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
            }
        }
    }
}

@Composable
private fun PointLedgerRow(entry: PointLedgerEntry) {
    PointCard(Modifier.fillMaxWidth().heightIn(min = 88.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pointTypeText(entry.type), style = MaterialTheme.typography.titleMedium)
            Text(
                deltaText(entry.delta),
                color = if (entry.delta < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.points_balance_after, entry.balanceAfter))
            Text(stringResource(R.string.points_created_at, localizedTime(entry.createdAt)))
        }
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
