package com.pointquest.android.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointScaffold

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onOrders: () -> Unit,
    onPoints: () -> Unit,
    onRequestLogout: () -> Unit,
    onDismissLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
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
                        Text(stringResource(R.string.profile_points, state.user?.pointsBalance ?: 0))
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
