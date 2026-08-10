package com.pointquest.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteHostStoreTest {
    private val defaultHost = "https://default.example.test/"

    @Test
    fun usesDefaultWhenNothingHasBeenPersisted() {
        val persistence = MemoryPersistence()
        val store = store(persistence)

        assertEquals(defaultHost, store.currentHost)
        assertEquals(defaultHost, store.hostFlow.value)
    }

    @Test
    fun restoresAndNormalizesAValidPersistedHost() {
        val persistence = MemoryPersistence("https://saved.example.test")

        assertEquals("https://saved.example.test/", store(persistence).currentHost)
    }

    @Test
    fun fallsBackWhenPersistedHostIsInvalidOrUnreadable() {
        assertEquals(defaultHost, store(MemoryPersistence("https://saved.example.test/path")).currentHost)
        assertEquals(defaultHost, store(MemoryPersistence(failureOnRead = true)).currentHost)
    }

    @Test
    fun appliesValidHostOnlyAfterPersistingIt() {
        val persistence = MemoryPersistence()
        val store = store(persistence)

        val result = store.apply(" http://192.168.1.10:3000 ")

        assertEquals(RemoteHostApplyResult.Applied("http://192.168.1.10:3000/"), result)
        assertEquals("http://192.168.1.10:3000/", store.currentHost)
        assertEquals("http://192.168.1.10:3000/", store.hostFlow.value)
        assertEquals("http://192.168.1.10:3000/", persistence.value)
    }

    @Test
    fun rejectsInvalidHostWithoutOverwritingTheCurrentValue() {
        val persistence = MemoryPersistence()
        val store = store(persistence)

        val result = store.apply("https://api.example.test/api/v1/")

        assertEquals(RemoteHostApplyResult.Rejected(RemoteHostErrorCode.ROOT_PATH_ONLY), result)
        assertEquals(defaultHost, store.currentHost)
        assertEquals(null, persistence.value)
    }

    @Test
    fun reportsPersistenceFailureWithoutUpdatingTheCurrentValue() {
        val persistence = MemoryPersistence(failureOnWrite = true)
        val store = store(persistence)

        val result = store.apply("https://saved.example.test/")

        assertTrue(result is RemoteHostApplyResult.PersistenceFailed)
        assertEquals(defaultHost, store.currentHost)
        assertEquals(defaultHost, store.hostFlow.value)
    }

    private fun store(persistence: MemoryPersistence) = RemoteHostStore(
        defaultHost = defaultHost,
        persistence = persistence,
        validator = RemoteHostValidator(allowHttp = true),
    )

    private class MemoryPersistence(
        initialValue: String? = null,
        private val failureOnRead: Boolean = false,
        private val failureOnWrite: Boolean = false,
    ) : RemoteHostPersistence {
        var value: String? = initialValue

        override fun read(): String? {
            if (failureOnRead) error("read failed")
            return value
        }

        override fun write(value: String) {
            if (failureOnWrite) error("write failed")
            this.value = value
        }
    }
}
