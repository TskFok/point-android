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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.selected
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
    onPractice: () -> Unit = {},
    onProfile: () -> Unit = {},
    onWrongQuestions: () -> Unit = {},
    onHome: () -> Unit = {},
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
                onWrongQuestions = onWrongQuestions,
                onProfile = onProfile,
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
                onPractice = onPractice,
                onProfile = onProfile,
                onHome = onHome,
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
    onWrongQuestions: () -> Unit,
    onProfile: () -> Unit,
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
                    Text(
                        text = stringResource(R.string.paper_practice_preview_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.preview_setup_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.preview_setup_copy),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.paper_practice_preview_count_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PreviewUiState.PRESET_COUNTS.forEach { preset ->
                            val selected = state.count == preset
                            OutlinedButton(
                                onClick = { onCountChange(preset) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("preview_count_$preset")
                                    .semantics { this.selected = selected },
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    contentColor = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                ),
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
                val empty = previewEmptyCopy(state.language)
                PointCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(empty.titleText(), style = MaterialTheme.typography.titleMedium)
                        Text(empty.descriptionText())
                        TextButton(
                            onClick = onWrongQuestions,
                            modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth().testTag("preview_empty_wrong_questions"),
                        ) {
                            Text(stringResource(R.string.preview_empty_pool_wrong_action))
                        }
                        if (empty.profileHint) {
                            TextButton(
                                onClick = onProfile,
                                modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth().testTag("preview_empty_profile"),
                            ) {
                                Text(stringResource(R.string.preview_empty_pool_profile_action))
                            }
                        }
                    }
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.preview_progress, state.currentIndex + 1, state.items.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
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
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = state.currentIndex > 0 && !state.submitting,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("preview_previous"),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(stringResource(R.string.preview_previous))
                }
                PointPrimaryButton(
                    text = stringResource(R.string.preview_next),
                    onClick = onNext,
                    enabled = item.answered && state.currentIndex < state.items.lastIndex && !state.submitting,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("preview_next"),
                )
            }
        }
    }
}

@Composable
private fun PreviewSummary(
    state: PreviewUiState,
    onReset: () -> Unit,
    onPractice: () -> Unit,
    onProfile: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        item {
            PointCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.paper_practice_summary_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.preview_summary_title), style = MaterialTheme.typography.headlineSmall)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.preview_summary_correct, state.correctCount))
                    Text(stringResource(R.string.preview_summary_skipped, state.skippedCount))
                    Text(stringResource(R.string.preview_summary_points, state.pointsEarned))
                    PointPrimaryButton(
                        text = stringResource(R.string.preview_reset),
                        onClick = onReset,
                        modifier = Modifier.testTag("preview_reset"),
                    )
                    TextButton(onClick = onPractice, modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth()) {
                        Text(stringResource(R.string.practice_completed_action))
                    }
                    TextButton(onClick = onProfile, modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth()) {
                        Text(stringResource(R.string.profile_title))
                    }
                    TextButton(onClick = onHome, modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth()) {
                        Text(stringResource(R.string.preview_home_action))
                    }
                }
            }
        }
    }
}
