package com.pointquest.android.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.ui.asString
import com.pointquest.android.core.ui.components.PagedListFooter
import com.pointquest.android.core.ui.components.PagedListFooterState
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointScaffold
import com.pointquest.android.core.ui.labelRes
import com.pointquest.android.feature.points.PointLedgerRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onOrders: () -> Unit,
    onPoints: () -> Unit,
    onRequestLogout: () -> Unit,
    onDismissLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
    onLanguageChange: (LearnerLanguage) -> Unit,
    onRetry: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    PointScaffold(
        title = stringResource(R.string.profile_title),
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PointCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.paper_profile_account),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(state.user?.username.orEmpty(), style = MaterialTheme.typography.headlineLarge)
                        Text(
                            text = stringResource(R.string.profile_student_role),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SelectionContainer {
                            Text(
                                text = stringResource(R.string.profile_account_id, state.user?.id.orEmpty()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(
                            text = stringResource(R.string.profile_points, state.user?.pointsBalance ?: 0),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            item {
                PointCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.paper_profile_settings),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.profile_language_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LearnerLanguage.entries.forEach { language ->
                                LanguageAction(
                                    text = stringResource(language.labelRes()),
                                    selected = language == state.language,
                                    enabled = !state.loggingOut,
                                ) {
                                    onLanguageChange(language)
                                }
                            }
                        }
                        state.languagePersistenceError?.let { error ->
                            Text(
                                text = error.asString(),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            item {
                PointCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = stringResource(R.string.paper_profile_records),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
                        )
                        SettingAction(
                            text = stringResource(R.string.profile_orders),
                            enabled = !state.loggingOut,
                            onClick = onOrders,
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                        SettingAction(
                            text = stringResource(R.string.profile_points_ledger),
                            enabled = !state.loggingOut,
                            onClick = onPoints,
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.profile_ledger_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            when {
                state.loading && state.items.isEmpty() -> item {
                    val loading = stringResource(R.string.loading)
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
                }
                state.error != null && state.items.isEmpty() -> item {
                    PointCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(state.error.asString(), color = MaterialTheme.colorScheme.error)
                            TextButton(
                                onClick = onRetry,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("profile_ledger_retry"),
                            ) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
                state.empty -> item {
                    PointCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.profile_ledger_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.profile_ledger_empty_copy),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    items(state.items, key = PointLedgerEntry::id) { entry ->
                        PointLedgerRow(entry)
                    }
                    item {
                        when {
                            state.loadingMore -> PagedListFooter(PagedListFooterState.Loading, onLoadMore)
                            state.loadMoreError != null -> PagedListFooter(
                                PagedListFooterState.Error(state.loadMoreError),
                                onLoadMore,
                            )
                            state.canLoadMore -> TextButton(
                                onClick = onLoadMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_ledger_load_more"),
                            ) { Text(stringResource(R.string.load_more)) }
                            else -> PagedListFooter(PagedListFooterState.End, onLoadMore)
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onRequestLogout,
                    enabled = !state.loggingOut,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        if (state.loggingOut) stringResource(R.string.profile_logging_out)
                        else stringResource(R.string.profile_logout),
                    )
                }
            }
        }
    }

    if (state.showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!state.loggingOut) onDismissLogout() },
            title = { Text(stringResource(R.string.profile_logout_confirm_title)) },
            text = { Text(stringResource(R.string.profile_logout_confirm_message)) },
            confirmButton = {
                Button(onClick = onConfirmLogout, enabled = !state.loggingOut) {
                    Text(stringResource(R.string.profile_logout_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissLogout, enabled = !state.loggingOut) {
                    Text(stringResource(R.string.profile_logout_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val actionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    val chevronColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, color = actionColor, modifier = Modifier.weight(1f))
        Canvas(Modifier.size(20.dp)) {
            val strokeWidth = 2.dp.toPx()
            drawLine(
                color = chevronColor,
                start = Offset(size.width * 0.38f, size.height * 0.24f),
                end = Offset(size.width * 0.62f, size.height * 0.5f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = chevronColor,
                start = Offset(size.width * 0.62f, size.height * 0.5f),
                end = Offset(size.width * 0.38f, size.height * 0.76f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun LanguageAction(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                if (selected) stringResource(R.string.profile_language_selected, text)
                else text,
            )
        },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}
