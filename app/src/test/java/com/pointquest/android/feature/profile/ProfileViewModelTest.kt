package com.pointquest.android.feature.profile

import com.pointquest.android.core.auth.ActiveSession
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.data.auth.AuthRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileViewModelTest {
    @Test
    fun profileTracksCurrentSessionUserInRealTime() {
        val sessionState = SessionState()
        val viewModel = ProfileViewModel(FakeAuthRepository(), sessionState, testScope())

        sessionState.publish(activeSession("student", 42, 1))
        assertEquals("student", viewModel.uiState.value.user?.username)
        assertEquals(42, viewModel.uiState.value.user?.pointsBalance)

        sessionState.publish(activeSession("student_new", 88, 2))
        assertEquals("student_new", viewModel.uiState.value.user?.username)
        assertEquals(88, viewModel.uiState.value.user?.pointsBalance)
    }

    @Test
    fun logoutRequiresConfirmationAndDuplicateConfirmationIsIgnored() = runBlocking {
        val logoutDeferred = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository(logoutDeferred)
        val viewModel = ProfileViewModel(repository, SessionState(), testScope())

        viewModel.requestLogout()
        assertTrue(viewModel.uiState.value.showLogoutConfirmation)
        val first = viewModel.confirmLogout()
        val duplicate = viewModel.confirmLogout()

        assertEquals(1, repository.logoutCalls)
        assertNull(duplicate)
        assertTrue(viewModel.uiState.value.loggingOut)
        logoutDeferred.complete(Unit)
        first!!.join()
        assertFalse(viewModel.uiState.value.loggingOut)
        assertFalse(viewModel.uiState.value.showLogoutConfirmation)
    }

    @Test
    fun answerOrRedeemBalanceImmediatelyUpdatesExistingProfileState() {
        val sessionState = SessionState().apply { publish(activeSession("student", 42, 1)) }
        val sync = AppDataSync(sessionState)
        val viewModel = ProfileViewModel(
            FakeAuthRepository(),
            sessionState,
            scopeOverride = testScope(),
            appDataSync = sync,
        )

        sync.recordOrderCreated(checkNotNull(sync.captureSession()), balance = 17)

        assertEquals(17, viewModel.uiState.value.user?.pointsBalance)
    }

    @Test
    fun signedOutAndAccountSwitchRejectsOldBalanceWhileCurrentAccountUpdatesStillApply() {
        val sessionState = SessionState().apply {
            publish(activeSession("student-a", 42, 1, userId = "student-a-id"))
        }
        val sync = AppDataSync(sessionState)
        val viewModel = ProfileViewModel(
            FakeAuthRepository(),
            sessionState,
            scopeOverride = testScope(),
            appDataSync = sync,
        )
        val accountASession = checkNotNull(sync.captureSession())
        sync.recordOrderCreated(accountASession, balance = 17)
        assertEquals(17, viewModel.uiState.value.user?.pointsBalance)

        sessionState.clear()
        sessionState.publish(activeSession("student-b", 88, 2, userId = "student-b-id"))
        sync.recordOrderCreated(accountASession, balance = 5)

        assertEquals("student-b", viewModel.uiState.value.user?.username)
        assertEquals(88, viewModel.uiState.value.user?.pointsBalance)

        sync.recordPracticeChanged(checkNotNull(sync.captureSession()), balance = 77)
        assertEquals(77, viewModel.uiState.value.user?.pointsBalance)
    }

    private class FakeAuthRepository(
        private val logoutDeferred: CompletableDeferred<Unit>? = null,
    ) : AuthRepository {
        var logoutCalls = 0
        override suspend fun register(username: String, password: String): AppResult<User> = error("unused")
        override suspend fun login(username: String, password: String): AppResult<User> = error("unused")
        override suspend fun restore(): AppResult<User> = error("unused")
        override suspend fun logout() {
            logoutCalls++
            logoutDeferred?.await()
        }
    }

    private companion object {
        fun testScope() = CoroutineScope(Job() + Dispatchers.Unconfined)
        fun activeSession(
            username: String,
            points: Int,
            generation: Long,
            userId: String = "student-1",
        ) = ActiveSession(
            user = User(userId, username, UserRole.STUDENT, points),
            accessToken = "token",
            accessTokenExpiresAt = Instant.parse("2030-01-01T00:05:00Z"),
            generation = generation,
            loginSessionId = generation,
        )
    }
}
