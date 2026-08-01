package com.pointquest.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.ui.asString
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointScaffold

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onStartPractice: () -> Unit,
    onWrongQuestions: () -> Unit,
    onOrders: () -> Unit,
    onPoints: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    PointScaffold(
        title = stringResource(R.string.home_title),
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.home_welcome, state.username),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.home_points_label), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.balance?.toString() ?: stringResource(R.string.home_points_unknown),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
            }
            if (state.loading && state.summary == null) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
            }
            state.error?.let { error ->
                item {
                    PointCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(error.asString(), color = MaterialTheme.colorScheme.onSurface)
                            if (state.canRetry) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
            }
            state.summary?.let { summary ->
                item {
                    PointCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.home_progress_title), style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(
                                progress = {
                                    if (summary.activeTotal == 0) 0f
                                    else summary.firstAnsweredCount.toFloat() / summary.activeTotal
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(stringResource(R.string.home_progress_copy, summary.firstAnsweredCount, summary.activeTotal))
                            Text(stringResource(R.string.home_unanswered_copy, summary.unansweredCount))
                            Text(stringResource(R.string.home_wrong_copy, summary.pendingWrongCount, summary.masteredWrongCount))
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onStartPractice,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) { Text(stringResource(R.string.home_start_practice)) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_quick_actions), style = MaterialTheme.typography.titleMedium)
                    QuickAction(stringResource(R.string.home_wrong_questions), onWrongQuestions)
                    QuickAction(stringResource(R.string.home_orders), onOrders)
                    QuickAction(stringResource(R.string.home_points), onPoints)
                }
            }
        }
    }
}

@Composable
private fun QuickAction(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.outlinedButtonColors(),
    ) { Text(text) }
}
