package com.pointquest.android.feature.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.pointquest.android.R
import com.pointquest.android.core.model.AnswerResult
import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.QuestionOption
import com.pointquest.android.core.ui.components.PointCard

@Composable
fun QuestionContent(
    question: Question,
    selectedOptionId: String?,
    selectionEnabled: Boolean,
    result: AnswerResult?,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PointCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(question.stem, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.question_base_points, question.basePoints))
            }
        }
        question.options.sortedBy { it.position }.forEach { option ->
            QuestionOptionCard(
                option = option,
                selectedOptionId = selectedOptionId,
                selectionEnabled = selectionEnabled,
                result = result,
                onSelectOption = onSelectOption,
            )
        }
    }
}

@Composable
private fun QuestionOptionCard(
    option: QuestionOption,
    selectedOptionId: String?,
    selectionEnabled: Boolean,
    result: AnswerResult?,
    onSelectOption: (String) -> Unit,
) {
    val selected = selectedOptionId == option.id
    val correct = result != null && result.correctOptionId == option.id
    val wrong = result != null && selected && !correct
    val answerStatus = when {
        correct -> PracticeAnswerStatus.Correct
        wrong -> PracticeAnswerStatus.Incorrect
        else -> null
    }
    val statusColors = answerStatus?.let {
        PracticeStatusColors.option(it, MaterialTheme.colorScheme.background)
    }
    val stateCopy = stringResource(
        when {
            correct -> R.string.option_correct_state
            wrong -> R.string.option_wrong_selected_state
            selected -> R.string.option_selected_state
            else -> R.string.option_unselected_state
        },
    )
    val accent = when {
        statusColors != null -> statusColors.accent
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val background = when {
        statusColors != null -> statusColors.container
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = .1f)
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("question_option_${option.id}")
            .semantics {
                stateDescription = stateCopy
                role = Role.RadioButton
            }
            .selectable(
                selected = selected,
                enabled = selectionEnabled,
                role = Role.RadioButton,
                onClick = { onSelectOption(option.id) },
            ),
        colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(if (selected || correct || wrong) 2.dp else 1.dp, accent),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                option.label,
                style = MaterialTheme.typography.labelLarge,
                color = statusColors?.text ?: accent,
            )
            Text(option.content, style = MaterialTheme.typography.bodyLarge)
            when {
                correct -> OptionStatus(
                    text = stringResource(R.string.option_correct),
                    description = stringResource(R.string.option_correct_icon),
                    textColor = requireNotNull(statusColors).text,
                    iconColor = statusColors.icon,
                    correct = true,
                )
                wrong -> OptionStatus(
                    text = stringResource(R.string.option_wrong),
                    description = stringResource(R.string.option_wrong_icon),
                    textColor = requireNotNull(statusColors).text,
                    iconColor = statusColors.icon,
                    correct = false,
                )
            }
        }
    }
}

@Composable
private fun OptionStatus(
    text: String,
    description: String,
    textColor: Color,
    iconColor: Color,
    correct: Boolean,
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val stroke = with(LocalDensity.current) { 2.5.dp.toPx() }
        Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) {
            if (correct) {
                drawLine(iconColor, Offset(size.width * .18f, size.height * .52f), Offset(size.width * .42f, size.height * .76f), stroke, StrokeCap.Round)
                drawLine(iconColor, Offset(size.width * .42f, size.height * .76f), Offset(size.width * .84f, size.height * .25f), stroke, StrokeCap.Round)
            } else {
                drawLine(iconColor, Offset(size.width * .24f, size.height * .24f), Offset(size.width * .76f, size.height * .76f), stroke, StrokeCap.Round)
                drawLine(iconColor, Offset(size.width * .76f, size.height * .24f), Offset(size.width * .24f, size.height * .76f), stroke, StrokeCap.Round)
            }
        }
        Text(text, color = textColor, style = MaterialTheme.typography.labelLarge)
    }
}
