package com.pointquest.android.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.app.PracticeMode
import com.pointquest.android.core.ui.asString
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointPrimaryButton
import com.pointquest.android.core.ui.components.PointScaffold

@Composable
fun QuestionScreen(
    state: QuestionUiState,
    onSelectOption: (String) -> Unit,
    onSubmit: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onRetryTailLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PointScaffold(title = stringResource(R.string.question_title), modifier = modifier) { padding ->
        when {
            state.loading && state.queue.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val loading = stringResource(R.string.loading)
                CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
            }
            state.question != null -> {
                val question = checkNotNull(state.question)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        QuestionProgress(state, onPrevious, onNext)
                    }
                    item {
                        QuestionContent(
                            question = question,
                            selectedOptionId = state.selectedOptionId,
                            selectionEnabled = state.selectionEnabled,
                            result = state.result,
                            onSelectOption = onSelectOption,
                        )
                    }
                state.error?.let { message ->
                    item {
                        Text(
                            message.asString(),
                            color = PracticeStatusColors.result(
                                PracticeAnswerStatus.Incorrect,
                                MaterialTheme.colorScheme.background,
                            ).text,
                        )
                    }
                }
                state.currentItem?.submitError?.let { message ->
                    item {
                        Text(
                            message.asString(),
                            color = PracticeStatusColors.result(
                                PracticeAnswerStatus.Incorrect,
                                MaterialTheme.colorScheme.background,
                            ).text,
                        )
                    }
                }
                if (!state.submitted) {
                    item {
                        PointPrimaryButton(
                            text = stringResource(
                                when {
                                    state.submitting -> R.string.answer_submitting
                                    state.currentItem?.submitError != null -> R.string.preview_retry_submit
                                    else -> R.string.answer_submit
                                },
                            ),
                            onClick = onSubmit,
                            modifier = Modifier.testTag("question_submit"),
                            enabled = state.submitEnabled,
                        )
                    }
                }
                state.result?.let { result ->
                    item { AnswerResultCard(result, Modifier.fillMaxWidth()) }
                    if (state.mode == PracticeMode.WRONG) {
                        item {
                            PointPrimaryButton(
                                text = stringResource(R.string.answer_back_to_wrong),
                                onClick = onNext,
                                modifier = Modifier.testTag("question_next"),
                            )
                        }
                    }
                }
                if (state.loadingNext) {
                    item {
                        val loading = stringResource(R.string.loading)
                        CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
                    }
                }
                state.tailError?.let { message ->
                    item { TailLoadError(message.asString(), onRetryTailLoad) }
                }
                if (state.completed && state.mode == PracticeMode.FIRST) {
                    item {
                        Text(
                            stringResource(R.string.practice_completed_copy),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                }
            }
            state.completed -> PracticeCompleted(onNext, Modifier.padding(padding))
            state.question == null && state.error != null -> QuestionError(state, onRetry, Modifier.padding(padding))
            else -> QuestionError(state, onRetry, Modifier.padding(padding))
        }
    }
}

@Composable
private fun QuestionProgress(
    state: QuestionUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "第 ${state.currentIndex + 1} / ${state.queue.size} 题",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onPrevious,
                modifier = Modifier.heightIn(min = 48.dp).testTag("question_previous"),
                enabled = state.hasPrevious && !state.submitting && !state.loadingNext,
            ) {
                Text(stringResource(R.string.preview_previous))
            }
            PointPrimaryButton(
                text = stringResource(R.string.answer_next),
                onClick = onNext,
                modifier = Modifier.weight(1f).testTag("question_next"),
                enabled = !state.submitting &&
                    !state.loadingNext &&
                    !state.completed &&
                    (state.hasNextInQueue || (state.mode == PracticeMode.FIRST && state.submitted)),
            )
        }
    }
}

@Composable
private fun TailLoadError(message: String, onRetryTailLoad: () -> Unit) {
    PointCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                message,
                color = PracticeStatusColors.result(
                    PracticeAnswerStatus.Incorrect,
                    MaterialTheme.colorScheme.background,
                ).text,
            )
            TextButton(onClick = onRetryTailLoad, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun PracticeCompleted(onNext: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PointCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.practice_completed_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.practice_completed_copy))
                PointPrimaryButton(stringResource(R.string.practice_completed_action), onNext)
            }
        }
    }
}

@Composable
private fun QuestionError(state: QuestionUiState, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        PointCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.error?.asString() ?: stringResource(R.string.load_failed))
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
