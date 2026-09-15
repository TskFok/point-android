package com.pointquest.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointquest.android.R
import com.pointquest.android.core.ui.asString
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointPrimaryButton
import com.pointquest.android.core.ui.components.PointScaffold

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onStartPractice: () -> Unit,
    onPreview: () -> Unit = {},
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { PaperGreeting(username = state.username) }
            if (state.loading && state.summary == null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            state.error?.let { error ->
                item {
                    PointCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(error.asString(), color = MaterialTheme.colorScheme.onSurface)
                            if (state.canRetry) {
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            }
                        }
                    }
                }
            }
            state.summary?.let { summary ->
                item {
                    val progress = if (summary.activeTotal == 0) {
                        0f
                    } else {
                        (summary.firstAnsweredCount.toFloat() / summary.activeTotal).coerceIn(0f, 1f)
                    }
                    val progressPercent = (progress * 100).toInt()
                    Column {
                        val fontScale = LocalDensity.current.fontScale
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            val progressDigits = summary.firstAnsweredCount.toString().length +
                                summary.activeTotal.toString().length
                            val stacked = maxWidth < 300.dp || fontScale >= 1.3f || progressDigits >= 6
                            if (stacked) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PaperProgressNumber(
                                        completed = summary.firstAnsweredCount,
                                        total = summary.activeTotal,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    PaperProgressDetail(
                                        progress = progress,
                                        progressPercent = progressPercent,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PaperProgressNumber(
                                        completed = summary.firstAnsweredCount,
                                        total = summary.activeTotal,
                                        modifier = Modifier.weight(0.9f),
                                    )
                                    PaperProgressDetail(
                                        progress = progress,
                                        progressPercent = progressPercent,
                                        modifier = Modifier.weight(1.1f),
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                        PaperMetricRow(
                            label = stringResource(R.string.paper_home_unanswered),
                            value = summary.unansweredCount,
                        )
                        PaperMetricRow(
                            label = stringResource(R.string.paper_home_pending_wrong),
                            value = summary.pendingWrongCount,
                        )
                        PaperMetricRow(
                            label = stringResource(R.string.paper_home_mastered_wrong),
                            value = summary.masteredWrongCount,
                        )
                    }
                }
                if (summary.unansweredCount == 0 && summary.pendingWrongCount == 0) {
                    item {
                        PointCard(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.home_empty_title),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = stringResource(R.string.home_empty_copy),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = onPreview,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.home_preview_action))
                                }
                            }
                        }
                    }
                }
            }
            item {
                PointPrimaryButton(
                    text = stringResource(R.string.home_start_practice),
                    onClick = onStartPractice,
                )
            }
            item {
                PointCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_points_label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = state.balance?.toString() ?: stringResource(R.string.home_points_unknown),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.paper_home_points_unit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    QuickAction(stringResource(R.string.home_wrong_questions), onWrongQuestions)
                    HorizontalDivider()
                    QuickAction(stringResource(R.string.home_orders), onOrders)
                    HorizontalDivider()
                    QuickAction(stringResource(R.string.home_points), onPoints)
                }
            }
        }
    }
}

@Composable
private fun PaperProgressNumber(completed: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = completed.toString(),
            style = MaterialTheme.typography.displayLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = stringResource(R.string.paper_home_progress_total, total),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 5.dp),
        )
    }
}

@Composable
private fun PaperProgressDetail(progress: Float, progressPercent: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.home_progress_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.paper_home_progress_percent, progressPercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
    }
}

@Composable
private fun PaperGreeting(username: String) {
    val lead = stringResource(R.string.paper_home_headline_lead)
    val emphasis = stringResource(R.string.paper_home_headline_emphasis)
    val accent = MaterialTheme.colorScheme.primary
    Column {
        Text(
            text = stringResource(R.string.paper_home_journal),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(
            modifier = Modifier.padding(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_welcome, username),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildAnnotatedString {
                    append(lead)
                    append('\n')
                    withStyle(SpanStyle(color = accent)) {
                        append(emphasis)
                    }
                },
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun PaperMetricRow(label: String, value: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.paper_home_question_unit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
    HorizontalDivider()
}

@Composable
private fun QuickAction(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Text(text)
    }
}
