package com.pointquest.android.feature.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.ui.components.PointCard

@Composable
fun AnswerResultCard(
    result: AnswerResult?,
    modifier: Modifier = Modifier,
    skipped: Boolean = false,
) {
    val effectiveSkipped = skipped || result == null
    val title = stringResource(
        when {
            effectiveSkipped -> R.string.answer_skipped
            result.correct -> R.string.answer_correct
            else -> R.string.answer_incorrect
        },
    )
    val status = if (result?.correct == false) PracticeAnswerStatus.Incorrect else PracticeAnswerStatus.Correct
    val colors = PracticeStatusColors.result(status, MaterialTheme.colorScheme.surface)
    PointCard(modifier) {
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
                Text(stringResource(R.string.answer_skipped_copy), style = MaterialTheme.typography.bodyLarge)
            } else {
                requireNotNull(result)
                Text(result.explanation, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.answer_points_awarded, result.pointsAwarded))
                Text(stringResource(R.string.answer_error_count, result.errorCount))
                Text(stringResource(R.string.answer_balance, result.balance))
            }
        }
    }
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
