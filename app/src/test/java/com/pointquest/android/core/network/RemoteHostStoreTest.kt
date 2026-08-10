package com.pointquest.android.core.network

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun appliesAreSerializedAcrossPersistenceAndPublication() {
        val persistence = BlockingFirstWritePersistence()
        val store = store(persistence)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<RemoteHostApplyResult> {
                store.apply("https://first.example.test/")
            }
            assertTrue(persistence.awaitFirstWrite())

            val second = executor.submit<RemoteHostApplyResult> {
                store.apply("https://second.example.test/")
            }

            assertFalse(
                "A concurrent apply must not enter persistence before the first apply publishes",
                persistence.awaitSecondWrite(),
            )
            persistence.releaseFirstWrite()

            assertEquals(
                RemoteHostApplyResult.Applied("https://first.example.test/"),
                first.get(5, TimeUnit.SECONDS),
            )
            assertEquals(
                RemoteHostApplyResult.Applied("https://second.example.test/"),
                second.get(5, TimeUnit.SECONDS),
            )
            assertEquals("https://second.example.test/", persistence.value)
            assertEquals("https://second.example.test/", store.currentHost)
        } finally {
            persistence.releaseFirstWrite()
            executor.shutdownNow()
        }
    }

    private fun store(persistence: RemoteHostPersistence) = RemoteHostStore(
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

    private class BlockingFirstWritePersistence : RemoteHostPersistence {
        private val writes = AtomicInteger()
        private val firstWriteEntered = CountDownLatch(1)
        private val releaseFirstWrite = CountDownLatch(1)
        private val secondWriteEntered = CountDownLatch(1)

        @Volatile
        var value: String? = null
            private set

        override fun read(): String? = null

        override fun write(value: String) {
            this.value = value
            when (writes.incrementAndGet()) {
                1 -> {
                    firstWriteEntered.countDown()
                    check(releaseFirstWrite.await(5, TimeUnit.SECONDS)) {
                        "Timed out waiting to release the first write"
                    }
                }
                2 -> secondWriteEntered.countDown()
            }
        }

        fun awaitFirstWrite(): Boolean = firstWriteEntered.await(5, TimeUnit.SECONDS)

        fun awaitSecondWrite(): Boolean = secondWriteEntered.await(1, TimeUnit.SECONDS)

        fun releaseFirstWrite() = releaseFirstWrite.countDown()
    }
}
