package com.pointquest.android.data.preferences

import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.test.MemoryLearnerLanguagePersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnerLanguageStoreTest {
    @Test
    fun invalidOrMissingValueUsesAllLanguagesAndAllRemovesTheStoredCode() {
        val persistence = MemoryLearnerLanguagePersistence("xx")
        val store = DefaultLearnerLanguageStore(persistence)

        assertEquals(LearnerLanguage.ALL, store.language.value)
        assertTrue(store.setLanguage(LearnerLanguage.JA))
        assertEquals("ja", persistence.value)
        assertTrue(store.setLanguage(LearnerLanguage.ALL))
        assertNull(persistence.value)
        assertEquals(LearnerLanguage.ALL, store.language.value)
    }

    @Test
    fun restoresAValidPersistedLanguage() {
        val store = DefaultLearnerLanguageStore(MemoryLearnerLanguagePersistence("fr"))

        assertEquals(LearnerLanguage.FR, store.language.value)
    }

    @Test
    fun missingValueUsesAllLanguages() {
        val store = DefaultLearnerLanguageStore(MemoryLearnerLanguagePersistence())

        assertEquals(LearnerLanguage.ALL, store.language.value)
    }

    @Test
    fun persistenceFailureDoesNotPublishANewLanguage() {
        val persistence = FailingLearnerLanguagePersistence()
        val store = DefaultLearnerLanguageStore(persistence)

        assertFalse(store.setLanguage(LearnerLanguage.DE))
        assertEquals(LearnerLanguage.ALL, store.language.value)
        assertEquals(1, persistence.writeAttempts)
    }

    private class FailingLearnerLanguagePersistence : LearnerLanguagePersistence {
        var writeAttempts: Int = 0
            private set

        override fun read(): String? = null

        override fun write(value: String?) {
            writeAttempts += 1
            error("write failed")
        }
    }
}
