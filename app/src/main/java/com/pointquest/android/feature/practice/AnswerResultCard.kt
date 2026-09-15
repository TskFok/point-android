package com.pointquest.android.feature.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pointquest.android.app.PracticeMode
import com.pointquest.android.R
import com.pointquest.android.core.model.AnswerResult

@Composable
fun AnswerResultCard(
    result: AnswerResult?,
    modifier: Modifier = Modifier,
    skipped: Boolean = false,
    mode: PracticeMode = PracticeMode.FIRST,
) {
    val effectiveSkipped = skipped || result == null
    val title = stringResource(
        when {
            effectiveSkipped -> R.string.answer_skipped
            mode == PracticeMode.WRONG && result.correct -> R.string.answer_wrong_mastered
            result.correct -> R.string.answer_correct
            else -> R.string.answer_incorrect
        },
    )
    val status = if (result?.correct == false) PracticeAnswerStatus.Incorrect else PracticeAnswerStatus.Correct
    val colors = PracticeStatusColors.result(status, MaterialTheme.colorScheme.surface)
    val container = colors.accent.copy(alpha = .06f).compositeOver(MaterialTheme.colorScheme.surface)
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, colors.accent.copy(alpha = .4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResultIcon(
                    correct = result?.correct != false,
                    description = stringResource(
                        if (result?.correct == false) R.string.answer_incorrect_icon else R.string.answer_correct_icon,
                    ),
                    color = colors.icon,
                )
                Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text)
            }
            if (effectiveSkipped) {
                Text(
                    text = stringResource(R.string.answer_skipped_copy),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                requireNotNull(result)
                HorizontalDivider(color = colors.accent.copy(alpha = .3f))
                Text(
                    text = stringResource(R.string.paper_practice_result_explanation),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text,
                )
                Text(
                    text = result.explanation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (result.correct && mode == PracticeMode.FIRST) {
                    ResultMetric(stringResource(R.string.answer_points_awarded, result.pointsAwarded))
                }
                if (!result.correct) {
                    ResultMetric(stringResource(R.string.answer_error_count, result.errorCount))
                }
                if (mode == PracticeMode.WRONG) {
                    ResultMetric(stringResource(R.string.answer_wrong_no_reward))
                } else {
                    ResultMetric(stringResource(R.string.answer_balance, result.balance))
                }
            }
        }
    }
}

@Composable
private fun ResultMetric(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ResultIcon(correct: Boolean, description: String, color: androidx.compose.ui.graphics.Color) {
    val stroke = with(LocalDensity.current) { 3.dp.toPx() }
    Canvas(
        Modifier
            .size(32.dp)
            .semantics { contentDescription = description },
    ) {
        drawCircle(color, style = Stroke(stroke))
        if (correct) {
            drawLine(color, Offset(size.width * .25f, size.height * .52f), Offset(size.width * .43f, size.height * .7f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .43f, size.height * .7f), Offset(size.width * .76f, size.height * .32f), stroke, StrokeCap.Round)
        } else {
            drawLine(color, Offset(size.width * .3f, size.height * .3f), Offset(size.width * .7f, size.height * .7f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .7f, size.height * .3f), Offset(size.width * .3f, size.height * .7f), stroke, StrokeCap.Round)
        }
    }
}
