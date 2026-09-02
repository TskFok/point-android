package com.pointquest.android.feature.practice

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pointquest.android.R
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.ui.labelRes

data class LearnerEmptyCopy(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val languageLabelRes: Int? = null,
    val profileHint: Boolean = false,
)

fun practiceEmptyCopy(language: LearnerLanguage = LearnerLanguage.ALL): LearnerEmptyCopy =
    language.emptyCopy(
        allTitle = R.string.practice_completed_title,
        allDescription = R.string.practice_completed_copy,
        filteredTitle = R.string.practice_empty_filtered_title,
        filteredDescription = R.string.practice_empty_filtered_copy,
    )

fun previewEmptyCopy(language: LearnerLanguage = LearnerLanguage.ALL): LearnerEmptyCopy =
    language.emptyCopy(
        allTitle = R.string.preview_empty_title,
        allDescription = R.string.preview_empty_pool,
        filteredTitle = R.string.preview_empty_filtered_title,
        filteredDescription = R.string.preview_empty_filtered_copy,
    )

fun wrongQuestionsEmptyCopy(language: LearnerLanguage = LearnerLanguage.ALL): LearnerEmptyCopy =
    language.emptyCopy(
        allTitle = R.string.wrong_questions_empty_title,
        allDescription = R.string.wrong_questions_empty_copy,
        filteredTitle = R.string.wrong_questions_empty_filtered_title,
        filteredDescription = R.string.wrong_questions_empty_filtered_copy,
    )

private fun LearnerLanguage.emptyCopy(
    @StringRes allTitle: Int,
    @StringRes allDescription: Int,
    @StringRes filteredTitle: Int,
    @StringRes filteredDescription: Int,
): LearnerEmptyCopy {
    val labelRes = takeIf { it != LearnerLanguage.ALL }?.labelRes()
        ?: return LearnerEmptyCopy(titleRes = allTitle, descriptionRes = allDescription)
    return LearnerEmptyCopy(
        titleRes = filteredTitle,
        descriptionRes = filteredDescription,
        languageLabelRes = labelRes,
        profileHint = true,
    )
}

@Composable
fun LearnerEmptyCopy.titleText(): String {
    val label = languageLabelRes?.let { stringResource(it) }
    return if (label == null) stringResource(titleRes) else stringResource(titleRes, label)
}

@Composable
fun LearnerEmptyCopy.descriptionText(): String {
    val label = languageLabelRes?.let { stringResource(it) }
    return if (label == null) stringResource(descriptionRes) else stringResource(descriptionRes, label)
}
