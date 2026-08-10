package com.pointquest.android.core.model

enum class LearnerLanguage(val code: String?) {
    ALL(null),
    EN("en"),
    JA("ja"),
    IT("it"),
    FR("fr"),
    DE("de"),
    ;

    companion object {
        fun fromCode(value: String?): LearnerLanguage = entries.firstOrNull { it.code == value } ?: ALL
    }
}
