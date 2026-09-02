package com.pointquest.android.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.user?.username.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.profile_student_role))
                        SelectionContainer {
                            Text(stringResource(R.string.profile_account_id, state.user?.id.orEmpty()))
                        }
                        Text(stringResource(R.string.profile_points, state.user?.pointsBalance ?: 0))
                    }
                }
            }
            item {
                PointCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.profile_language_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LearnerLanguage.entries.forEach { language ->
                            LanguageAction(
                                text = stringResource(language.labelRes()),
                                selected = language == state.language,
                                enabled = !state.loggingOut,
                            ) {
                                onLanguageChange(language)
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
                Text(
                    stringResource(R.string.profile_ledger_title),
                    style = MaterialTheme.typography.titleMedium,
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
            item { ProfileAction(stringResource(R.string.profile_orders), !state.loggingOut, onOrders) }
            item { ProfileAction(stringResource(R.string.profile_points_ledger), !state.loggingOut, onPoints) }
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
private fun ProfileAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(text) }
}

@Composable
private fun LanguageAction(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(stringResource(R.string.profile_language_selected, text))
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text)
        }
    }
}
