package com.pointquest.android.core.ui

import androidx.annotation.StringRes
import com.pointquest.android.R
import com.pointquest.android.core.model.LearnerLanguage

@StringRes
fun LearnerLanguage.labelRes(): Int = when (this) {
    LearnerLanguage.ALL -> R.string.language_all
    LearnerLanguage.EN -> R.string.language_english
    LearnerLanguage.JA -> R.string.language_japanese
    LearnerLanguage.IT -> R.string.language_italian
    LearnerLanguage.FR -> R.string.language_french
    LearnerLanguage.DE -> R.string.language_german
}
