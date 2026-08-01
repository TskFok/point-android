package com.pointquest.android.feature.practice

import com.pointquest.android.core.model.Question
import com.pointquest.android.core.model.WrongQuestion
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeDraftStoreTest {
    @Test
    fun consumeReturnsStoredDraftExactlyOnce() {
        val store = PracticeDraftStore()
        store.put(wrongQuestion("q1"))

        assertEquals("q1", store.consume("q1")?.question?.id)
        assertNull(store.consume("q1"))
    }

    @Test
    fun concurrentConsumersCannotConsumeTheSameDraftTwice() {
        val store = PracticeDraftStore()
        store.put(wrongQuestion("q1"))
        val executor = Executors.newFixedThreadPool(8)

        val consumed = try {
            executor.invokeAll(List(32) { Callable { store.consume("q1") } })
                .mapNotNull { it.get() }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(listOf("q1"), consumed.map { it.question.id })
    }

    private fun wrongQuestion(id: String) = WrongQuestion(
        errorCount = 2,
        firstAnsweredAt = Instant.parse("2030-01-01T00:00:00Z"),
        masteredAt = null,
        question = Question(id, "题干", 5, emptyList()),
    )
}
