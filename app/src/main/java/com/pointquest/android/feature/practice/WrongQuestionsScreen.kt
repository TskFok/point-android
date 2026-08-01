package com.pointquest.android.feature.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.model.WrongQuestion
import com.pointquest.android.core.ui.asString
import com.pointquest.android.core.ui.components.AsyncContent
import com.pointquest.android.core.ui.components.AsyncState
import com.pointquest.android.core.ui.components.PagedListFooter
import com.pointquest.android.core.ui.components.PagedListFooterState
import com.pointquest.android.core.ui.components.PointCard
import com.pointquest.android.core.ui.components.PointScaffold

@Composable
fun WrongQuestionsScreen(
    state: WrongQuestionsUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSelectQuestion: (WrongQuestion) -> Unit,
    onNoticeShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = state.notice?.asString()
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            onNoticeShown()
        }
    }
    PointScaffold(title = stringResource(R.string.wrong_questions_title), modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val asyncState = when {
                state.loading && state.items.isEmpty() -> AsyncState.Loading
                state.error != null && state.items.isEmpty() -> AsyncState.Error(state.error)
                state.empty -> AsyncState.Empty
                else -> AsyncState.Content(state.items)
            }
            AsyncContent(
                state = asyncState,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            ) { questions ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    questions.forEach { wrongQuestion ->
                        item(key = wrongQuestion.question.id) {
                            WrongQuestionRow(wrongQuestion) { onSelectQuestion(wrongQuestion) }
                        }
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
                                    .testTag("wrong_load_more"),
                            ) { Text(stringResource(R.string.load_more)) }
                            else -> PagedListFooter(PagedListFooterState.End, onLoadMore)
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun WrongQuestionRow(question: WrongQuestion, onClick: () -> Unit) {
    PointCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("wrong_question_${question.question.id}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(question.question.stem, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wrong_question_error_count, question.errorCount))
            Text(
                stringResource(R.string.wrong_question_retry_action),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
