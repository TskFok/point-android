package com.pointquest.android.test

import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.data.preferences.LearnerLanguagePersistence
import com.pointquest.android.data.preferences.LearnerLanguageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MemoryLearnerLanguagePersistence(
    initialValue: String? = null,
    private val failureOnRead: Boolean = false,
    private val failureOnWrite: Boolean = false,
) : LearnerLanguagePersistence {
    var value: String? = initialValue
        private set

    override fun read(): String? {
        if (failureOnRead) error("read failed")
        return value
    }

    override fun write(value: String?) {
        if (failureOnWrite) error("write failed")
        this.value = value
    }
}

class FakeLearnerLanguageStore(initial: LearnerLanguage) : LearnerLanguageStore {
    private val mutable = MutableStateFlow(initial)

    override val language: StateFlow<LearnerLanguage> = mutable.asStateFlow()

    override fun setLanguage(value: LearnerLanguage): Boolean {
        mutable.value = value
        return true
    }
}
