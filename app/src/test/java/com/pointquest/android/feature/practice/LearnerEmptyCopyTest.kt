package com.pointquest.android.feature.practice

import com.pointquest.android.R
import com.pointquest.android.core.model.LearnerLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnerEmptyCopyTest {
    @Test
    fun allLanguageKeepsExistingPracticePreviewAndWrongCopy() {
        assertEquals(
            LearnerEmptyCopy(
                titleRes = R.string.practice_completed_title,
                descriptionRes = R.string.practice_completed_copy,
            ),
            practiceEmptyCopy(LearnerLanguage.ALL),
        )
        assertEquals(
            LearnerEmptyCopy(
                titleRes = R.string.preview_empty_title,
                descriptionRes = R.string.preview_empty_pool,
            ),
            previewEmptyCopy(LearnerLanguage.ALL),
        )
        assertEquals(
            LearnerEmptyCopy(
                titleRes = R.string.wrong_questions_empty_title,
                descriptionRes = R.string.wrong_questions_empty_copy,
            ),
            wrongQuestionsEmptyCopy(LearnerLanguage.ALL),
        )
    }

    @Test
    fun selectedLanguagePutsLanguageNameInTitlesAndHintsProfile() {
        assertEquals(
            LearnerEmptyCopy(
                titleRes = R.string.practice_empty_filtered_title,
                descriptionRes = R.string.practice_empty_filtered_copy,
                languageLabelRes = R.string.language_japanese,
                profileHint = true,
            ),
            practiceEmptyCopy(LearnerLanguage.JA),
        )
        assertEquals(
            LearnerEmptyCopy(
                titleRes = R.string.preview_empty_filtered_title,
                descriptionRes = R.string.preview_empty_filtered_copy,
                languageLabelRes = R.string.language_german,
                profileHint = true,
            ),
            previewEmptyCopy(LearnerLanguage.DE),
        )
        assertEquals(
            LearnerEmptyCopy(
                titleRes = R.string.wrong_questions_empty_filtered_title,
                descriptionRes = R.string.wrong_questions_empty_filtered_copy,
                languageLabelRes = R.string.language_italian,
                profileHint = true,
            ),
            wrongQuestionsEmptyCopy(LearnerLanguage.IT),
        )
    }

    @Test
    fun allLanguageDoesNotHintProfile() {
        assertFalse(practiceEmptyCopy(LearnerLanguage.ALL).profileHint)
        assertFalse(previewEmptyCopy(LearnerLanguage.ALL).profileHint)
        assertFalse(wrongQuestionsEmptyCopy(LearnerLanguage.ALL).profileHint)
        assertNull(practiceEmptyCopy(LearnerLanguage.ALL).languageLabelRes)
    }

    @Test
    fun everySpecificLanguageHintsProfile() {
        LearnerLanguage.entries
            .filter { it != LearnerLanguage.ALL }
            .forEach { language ->
                assertTrue(practiceEmptyCopy(language).profileHint)
                assertTrue(previewEmptyCopy(language).profileHint)
                assertTrue(wrongQuestionsEmptyCopy(language).profileHint)
            }
    }
}
