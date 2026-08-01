package com.pointquest.android.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.core.ui.asString

sealed interface PagedListFooterState {
    data object Idle : PagedListFooterState
    data object Loading : PagedListFooterState
    data class Error(val message: UiText) : PagedListFooterState
    data object End : PagedListFooterState
}

@Composable
fun PagedListFooter(
    state: PagedListFooterState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        PagedListFooterState.Idle -> Spacer(modifier.height(8.dp))
        PagedListFooterState.Loading -> {
            val description = stringResource(R.string.loading_more)
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(32.dp)
                        .semantics { contentDescription = description },
                )
            }
        }
        is PagedListFooterState.Error -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(state.message.asString())
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.retry)) }
        }
        PagedListFooterState.End -> Text(
            stringResource(R.string.list_end),
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
