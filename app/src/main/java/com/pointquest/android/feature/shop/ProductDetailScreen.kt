package com.pointquest.android.feature.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.ui.asString
import com.pointquest.android.core.ui.components.AsyncContent
import com.pointquest.android.core.ui.components.AsyncState
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointPrimaryButton
import com.pointquest.android.core.ui.components.PointScaffold
import com.pointquest.android.data.products.ProductImageUrlFactory

@Composable
fun ProductDetailScreen(
    state: ProductDetailUiState,
    imageUrlFactory: ProductImageUrlFactory,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onRequestRedeem: () -> Unit,
    onDismissRedeem: () -> Unit,
    onConfirmRedeem: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }
    PointScaffold(title = stringResource(R.string.product_detail_title), modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val asyncState = when {
                state.loading && state.product == null -> AsyncState.Loading
                state.error != null && state.product == null -> AsyncState.Error(state.error)
                state.product != null -> AsyncState.Content(state.product)
                else -> AsyncState.Empty
            }
            AsyncContent(asyncState, onRetry, Modifier.fillMaxSize()) { product ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("product_back"),
                    ) { Text(stringResource(R.string.back)) }
                    ProductImage(
                        product,
                        imageUrlFactory,
                        Modifier.size(180.dp).align(Alignment.CenterHorizontally),
                    )
                    Text(product.name, style = MaterialTheme.typography.headlineSmall)
                    Text(product.description, style = MaterialTheme.typography.bodyLarge)
                    PointCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.product_points_cost, product.pointsCost))
                            Text(stringResource(R.string.product_stock, product.stock.coerceAtLeast(0)))
                            Text(
                                state.balance?.let { stringResource(R.string.product_balance, it) }
                                    ?: stringResource(R.string.product_balance_unknown),
                            )
                            if (state.error != null) {
                                Text(state.error.asString(), color = MaterialTheme.colorScheme.error)
                                TextButton(
                                    onClick = onRetry,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.retry)) }
                            }
                        }
                    }
                    state.pointsDeficit?.let { deficit ->
                        Text(
                            stringResource(R.string.product_deficit, deficit),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("product_deficit"),
                        )
                    }
                    PointPrimaryButton(
                        text = if (state.redeeming) stringResource(R.string.product_redeeming)
                        else stringResource(R.string.product_redeem),
                        onClick = onRequestRedeem,
                        enabled = state.canRedeem,
                        modifier = Modifier.testTag("product_redeem"),
                    )
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (state.showRedeemConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissRedeem,
            title = { Text(stringResource(R.string.product_redeem_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.product_redeem_confirm_message,
                            state.product?.name.orEmpty(),
                            state.product?.pointsCost ?: 0,
                        ),
                    )
                    Text(stringResource(R.string.product_redeem_current, state.balance ?: 0))
                    Text(stringResource(R.string.product_redeem_cost, state.product?.pointsCost ?: 0))
                    Text(
                        stringResource(
                            R.string.product_redeem_remaining,
                            state.remainingBalanceAfterRedeem ?: 0,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmRedeem,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("product_redeem_confirm"),
                ) {
                    Text(
                        stringResource(
                            if (state.redeemRetryPending) R.string.product_redeem_retry
                            else R.string.product_redeem_confirm_action,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRedeem, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
