package com.pointquest.android.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.core.ui.asString

sealed interface AsyncState<out T> {
    data object Loading : AsyncState<Nothing>

    data class Content<T>(val value: T) : AsyncState<T>

    data object Empty : AsyncState<Nothing>

    data class Error(val message: UiText) : AsyncState<Nothing>
}

@Composable
fun <T> AsyncContent(
    state: AsyncState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        AsyncState.Loading -> LoadingState(modifier)
        is AsyncState.Content -> Box(modifier) { content(state.value) }
        AsyncState.Empty -> MessageCard(stringResource(R.string.empty_state), modifier)
        is AsyncState.Error -> ErrorState(state.message, onRetry, modifier)
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    val description = stringResource(R.string.loading)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun ErrorState(message: UiText, onRetry: () -> Unit, modifier: Modifier) {
    val description = stringResource(R.string.load_failed)
    PointCard(modifier.padding(16.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val errorColor = MaterialTheme.colorScheme.error
            val stroke = with(LocalDensity.current) { 3.dp.toPx() }
            Canvas(
                Modifier
                    .size(48.dp)
                    .semantics { contentDescription = description },
            ) {
                drawCircle(errorColor, style = Stroke(stroke))
                drawLine(
                    errorColor,
                    Offset(size.width / 2, size.height * 0.25f),
                    Offset(size.width / 2, size.height * 0.58f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawCircle(errorColor, radius = stroke / 2, center = Offset(size.width / 2, size.height * 0.75f))
            }
            Text(message.asString(), color = MaterialTheme.colorScheme.onSurface)
            PointPrimaryButton(text = stringResource(R.string.retry), onClick = onRetry)
        }
    }
}

@Composable
private fun MessageCard(message: String, modifier: Modifier) {
    PointCard(modifier.padding(16.dp)) {
        Text(message, modifier = Modifier.padding(20.dp))
    }
}
