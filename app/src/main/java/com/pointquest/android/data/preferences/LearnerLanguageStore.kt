package com.pointquest.android.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.pointquest.android.core.model.LearnerLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface LearnerLanguagePersistence {
    fun read(): String?

    fun write(value: String?)
}

class SharedPreferencesLearnerLanguagePersistence(
    context: Context,
) : LearnerLanguagePersistence {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(LEARNER_LANGUAGE_KEY, null)

    override fun write(value: String?) {
        val editor = preferences.edit()
        val persisted = if (value == null) {
            editor.remove(LEARNER_LANGUAGE_KEY).commit()
        } else {
            editor.putString(LEARNER_LANGUAGE_KEY, value).commit()
        }
        check(persisted) { "Unable to persist learner language" }
    }

    private companion object {
        const val PREFERENCES_NAME = "point_quest_settings"
        const val LEARNER_LANGUAGE_KEY = "learner_lang_code"
    }
}

interface LearnerLanguageStore {
    val language: StateFlow<LearnerLanguage>

    fun setLanguage(value: LearnerLanguage): Boolean
}

class DefaultLearnerLanguageStore(
    private val persistence: LearnerLanguagePersistence,
) : LearnerLanguageStore {
    private val updateLock = Any()
    private val mutableLanguage = MutableStateFlow(initialLanguage())

    override val language: StateFlow<LearnerLanguage> = mutableLanguage.asStateFlow()

    override fun setLanguage(value: LearnerLanguage): Boolean = synchronized(updateLock) {
        try {
            persistence.write(value.code)
            mutableLanguage.value = value
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun initialLanguage(): LearnerLanguage = try {
        LearnerLanguage.fromCode(persistence.read())
    } catch (_: Exception) {
        LearnerLanguage.ALL
    }
}
