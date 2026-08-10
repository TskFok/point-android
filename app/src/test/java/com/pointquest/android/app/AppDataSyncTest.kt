package com.pointquest.android.app

import com.pointquest.android.core.auth.ActiveSession
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataSyncTest {
    @Test
    fun practiceAndOrderResultsPublishBalanceAndInvalidateOnlineTopLevelData() {
        val sessionState = SessionState().apply { publish(activeSession("student-a", 1)) }
        val sync = AppDataSync(sessionState)
        val session = checkNotNull(sync.captureSession())

        sync.recordPracticeChanged(session, balance = 47)
        assertEquals(47, sync.state.value.balance)
        assertEquals(1L, sync.state.value.homeRefreshRevision)

        sync.recordOrderCreated(session, balance = 32)
        assertEquals(32, sync.state.value.balance)
        assertEquals(2L, sync.state.value.homeRefreshRevision)
        assertEquals(1L, sync.state.value.shopRefreshRevision)
    }

    @Test
    fun inactiveProductIsPublishedBeforeShopRefreshRevision() {
        val sessionState = SessionState().apply { publish(activeSession("student-a", 1)) }
        val sync = AppDataSync(sessionState)

        sync.recordProductInactive(checkNotNull(sync.captureSession()), "p1")

        assertTrue("p1" in sync.state.value.inactiveProductIds)
        assertEquals(1L, sync.state.value.shopRefreshRevision)
    }

    @Test
    fun newLoginSessionAtomicallyClearsSyncStateAndRejectsStaleWrites() {
        val sessionState = SessionState().apply { publish(activeSession("student-a", 1)) }
        val sync = AppDataSync(sessionState)
        val accountA = checkNotNull(sync.captureSession())
        sync.recordOrderCreated(accountA, 17)
        sync.recordProductInactive(accountA, "p1")

        sessionState.clear()

        assertNull(sync.state.value.session)
        assertNull(sync.state.value.balance)
        assertEquals(0L, sync.state.value.homeRefreshRevision)
        assertEquals(0L, sync.state.value.shopRefreshRevision)
        assertTrue(sync.state.value.inactiveProductIds.isEmpty())

        sessionState.publish(activeSession("student-a", 2))
        sync.recordOrderCreated(accountA, 5)

        assertEquals("student-a", sync.state.value.session?.userId)
        assertEquals(2L, sync.state.value.session?.loginSessionId)
        assertNull(sync.state.value.balance)
        assertEquals(0L, sync.state.value.homeRefreshRevision)
        assertEquals(0L, sync.state.value.shopRefreshRevision)
        assertTrue(sync.state.value.inactiveProductIds.isEmpty())

        sync.recordPracticeChanged(checkNotNull(sync.captureSession()), 88)
        assertEquals(88, sync.state.value.balance)
    }

    @Test
    fun sameAccountCredentialRefreshMustNotClearUnacknowledgedTombstone() {
        val sessionState = SessionState().apply { publish(activeSession("student-a", 1)) }
        val sync = AppDataSync(sessionState)
        sync.recordProductInactive(checkNotNull(sync.captureSession()), "p1")

        sessionState.publish(activeSession("student-a", 2, loginSessionId = 1))

        assertTrue("p1" in sync.state.value.inactiveProductIds)
    }

    private fun activeSession(
        userId: String,
        generation: Long,
        loginSessionId: Long = generation,
    ) = ActiveSession(
        user = User(userId, userId, UserRole.STUDENT, 42),
        accessToken = "token",
        accessTokenExpiresAt = Instant.parse("2030-01-01T00:05:00Z"),
        generation = generation,
        loginSessionId = loginSessionId,
    )
}
