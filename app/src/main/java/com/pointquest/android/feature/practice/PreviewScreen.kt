package com.pointquest.android.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.ui.asString
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointPrimaryButton
import com.pointquest.android.core.ui.components.PointScaffold

@Composable
fun PreviewScreen(
    state: PreviewUiState,
    onCountChange: (Int?) -> Unit,
    onStart: () -> Unit,
    onSelectOption: (String) -> Unit,
    onSubmit: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetryLoad: () -> Unit,
    onRetrySubmit: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PointScaffold(title = stringResource(R.string.preview_title), modifier = modifier) { padding ->
        when {
            state.loading -> PreviewLoading(Modifier.fillMaxSize().padding(padding))
            state.phase == PreviewPhase.SETUP -> PreviewSetup(
                state = state,
                onCountChange = onCountChange,
                onStart = onStart,
                onRetryLoad = onRetryLoad,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            state.phase == PreviewPhase.QUIZ -> PreviewQuiz(
                state = state,
                onSelectOption = onSelectOption,
                onSubmit = onSubmit,
                onPrevious = onPrevious,
                onNext = onNext,
                onRetrySubmit = onRetrySubmit,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            state.phase == PreviewPhase.SUMMARY -> PreviewSummary(
                state = state,
                onReset = onReset,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun PreviewLoading(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val loading = stringResource(R.string.loading)
        CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewSetup(
    state: PreviewUiState,
    onCountChange: (Int?) -> Unit,
    onStart: () -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PointCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.preview_setup_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.preview_setup_copy))
                    OutlinedTextField(
                        value = state.count?.toString().orEmpty(),
                        onValueChange = { raw -> onCountChange(raw.toIntOrNull()) },
                        label = { Text(stringResource(R.string.preview_count_label)) },
                        supportingText = { Text(stringResource(R.string.preview_count_helper)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("preview_count_input"),
                        isError = state.count != null && !state.countValid,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewUiState.PRESET_COUNTS.forEach { preset ->
                            TextButton(
                                onClick = { onCountChange(preset) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("preview_count_$preset"),
                            ) {
                                Text(stringResource(R.string.preview_count_preset, preset))
                            }
                        }
                    }
                    PointPrimaryButton(
                        text = stringResource(R.string.preview_start),
                        onClick = onStart,
                        modifier = Modifier.testTag("preview_start"),
                        enabled = state.countValid,
                    )
                }
            }
        }
        state.loadError?.let { error ->
            item {
                PointCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(error.asString())
                        TextButton(
                            onClick = onRetryLoad,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("preview_retry_load"),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
        if (state.emptyPool) {
            item {
                PointCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.preview_empty_pool),
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewQuiz(
    state: PreviewUiState,
    onSelectOption: (String) -> Unit,
    onSubmit: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetrySubmit: () -> Unit,
    modifier: Modifier,
) {
    val item = state.currentItem ?: return
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                stringResource(R.string.preview_progress, state.currentIndex + 1, state.items.size),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            QuestionContent(
                question = item.question,
                selectedOptionId = item.selectedOptionId,
                selectionEnabled = !state.submitting && !item.answered,
                result = item.result,
                onSelectOption = onSelectOption,
            )
        }
        item.submitError?.let { error ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        error.asString(),
                        color = PracticeStatusColors.result(
                            PracticeAnswerStatus.Incorrect,
                            MaterialTheme.colorScheme.background,
                        ).text,
                    )
                    TextButton(
                        onClick = onRetrySubmit,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("preview_retry_submit"),
                    ) {
                        Text(stringResource(R.string.preview_retry_submit))
                    }
                }
            }
        }
        if (!item.answered) {
            item {
                PointPrimaryButton(
                    text = stringResource(if (state.submitting) R.string.answer_submitting else R.string.answer_submit),
                    onClick = onSubmit,
                    modifier = Modifier.testTag("preview_submit"),
                    enabled = item.selectedOptionId != null && !state.submitting,
                )
            }
        }
        item.result?.let { result ->
            item { AnswerResultCard(result, Modifier.fillMaxWidth()) }
        }
        if (item.alreadyAnswered) {
            item { AnswerResultCard(result = null, modifier = Modifier.fillMaxWidth(), skipped = true) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onPrevious,
                    enabled = state.currentIndex > 0 && !state.submitting,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("preview_previous"),
                ) {
                    Text(stringResource(R.string.preview_previous))
                }
                TextButton(
                    onClick = onNext,
                    enabled = item.answered && state.currentIndex < state.items.lastIndex && !state.submitting,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("preview_next"),
                ) {
                    Text(stringResource(R.string.preview_next))
                }
            }
        }
    }
}

@Composable
private fun PreviewSummary(
    state: PreviewUiState,
    onReset: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        PointCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.preview_summary_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.preview_summary_correct, state.correctCount))
                Text(stringResource(R.string.preview_summary_skipped, state.skippedCount))
                Text(stringResource(R.string.preview_summary_points, state.pointsEarned))
                PointPrimaryButton(
                    text = stringResource(R.string.preview_reset),
                    onClick = onReset,
                    modifier = Modifier.testTag("preview_reset"),
                )
            }
        }
    }
}
