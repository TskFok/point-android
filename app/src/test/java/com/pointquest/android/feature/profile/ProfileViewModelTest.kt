package com.pointquest.android.feature.profile

import com.pointquest.android.R
import com.pointquest.android.core.auth.ActiveSession
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.Page
import com.pointquest.android.core.model.PageMeta
import com.pointquest.android.core.model.PointLedgerEntry
import com.pointquest.android.core.model.PointLedgerType
import com.pointquest.android.core.model.User
import com.pointquest.android.core.model.UserRole
import com.pointquest.android.core.network.AppResult
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.auth.AuthRepository
import com.pointquest.android.data.points.PointsRepository
import com.pointquest.android.data.preferences.LearnerLanguageStore
import com.pointquest.android.test.FakeLearnerLanguageStore
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileViewModelTest {
    @Test
    fun profilePublishesCurrentAccountIdAndSelectedLanguage() {
        val session = SessionState().apply { publish(activeSession("student-a", 42, 1, "id-a")) }
        val store = FakeLearnerLanguageStore(LearnerLanguage.ALL)
        val viewModel = ProfileViewModel(
            authRepository = FakeAuthRepository(),
            sessionState = session,
            learnerLanguageStore = store,
            scopeOverride = testScope(),
        )

        assertEquals("id-a", viewModel.uiState.value.user?.id)
        store.setLanguage(LearnerLanguage.DE)
        assertEquals(LearnerLanguage.DE, viewModel.uiState.value.language)

        session.publish(activeSession("student-b", 88, 2, "id-b"))
        assertEquals("id-b", viewModel.uiState.value.user?.id)
    }

    @Test
    fun languagePersistenceFailureKeepsPreviousSelectionAndUser() {
        val session = SessionState().apply { publish(activeSession("student-a", 42, 1, "id-a")) }
        val store = FailingLearnerLanguageStore(LearnerLanguage.FR)
        val viewModel = ProfileViewModel(
            authRepository = FakeAuthRepository(),
            sessionState = session,
            learnerLanguageStore = store,
            scopeOverride = testScope(),
        )

        viewModel.setLanguage(LearnerLanguage.DE)

        assertEquals(LearnerLanguage.FR, viewModel.uiState.value.language)
        assertEquals("id-a", viewModel.uiState.value.user?.id)
        assertEquals(1, store.attempts)
        assertEquals(
            R.string.profile_language_save_failed,
            (viewModel.uiState.value.languagePersistenceError as? UiText.Resource)?.id,
        )
    }

    @Test
    fun profileTracksCurrentSessionUserInRealTime() {
        val sessionState = SessionState()
        val viewModel = ProfileViewModel(
            FakeAuthRepository(),
            sessionState,
            FakeLearnerLanguageStore(LearnerLanguage.ALL),
            testScope(),
            pointsRepository = FakePointsRepository(),
        )

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
        val viewModel = ProfileViewModel(
            repository,
            SessionState(),
            FakeLearnerLanguageStore(LearnerLanguage.ALL),
            testScope(),
            pointsRepository = FakePointsRepository(),
        )

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
            FakeLearnerLanguageStore(LearnerLanguage.ALL),
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
            FakeLearnerLanguageStore(LearnerLanguage.ALL),
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

    @Test
    fun initializeLoadsCurrentUserBalanceAndLedgerTogether() = runBlocking {
        val sessionState = SessionState().apply { publish(activeSession("stale", 42, 1, "id-a")) }
        val auth = FakeAuthRepository(
            currentUserResult = AppResult.Success(User("id-a", "learner", UserRole.STUDENT, 160)),
        )
        val points = FakePointsRepository(
            balanceResult = AppResult.Success(160),
            ledger = listOf(ledgerEntry("ledger-1", 20, 160)),
        )
        val viewModel = ProfileViewModel(
            auth,
            sessionState,
            FakeLearnerLanguageStore(LearnerLanguage.ALL),
            testScope(),
            pointsRepository = points,
        )

        viewModel.initialize()?.join()

        assertEquals("learner", viewModel.uiState.value.user?.username)
        assertEquals(160, viewModel.uiState.value.user?.pointsBalance)
        assertEquals(listOf("ledger-1"), viewModel.uiState.value.items.map(PointLedgerEntry::id))
        assertEquals(1, auth.currentUserCalls)
        assertEquals(1, points.balanceCalls)
        assertEquals(listOf(1), points.pageCalls)
    }

    private class FakeAuthRepository(
        private val logoutDeferred: CompletableDeferred<Unit>? = null,
        private val currentUserResult: AppResult<User> = AppResult.Success(
            User("student-1", "student", UserRole.STUDENT, 42),
        ),
    ) : AuthRepository {
        var logoutCalls = 0
        var currentUserCalls = 0
        override suspend fun register(username: String, password: String): AppResult<User> = error("unused")
        override suspend fun login(username: String, password: String): AppResult<User> = error("unused")
        override suspend fun restore(): AppResult<User> = error("unused")
        override suspend fun currentUser(): AppResult<User> {
            currentUserCalls++
            return currentUserResult
        }
        override suspend fun logout() {
            logoutCalls++
            logoutDeferred?.await()
        }
    }

    private class FakePointsRepository(
        var balanceResult: AppResult<Int> = AppResult.Success(42),
        var ledger: List<PointLedgerEntry> = emptyList(),
    ) : PointsRepository {
        var balanceCalls = 0
        val pageCalls = mutableListOf<Int>()
        override suspend fun balance(): AppResult<Int> {
            balanceCalls++
            return balanceResult
        }
        override suspend fun ledger(page: Int): AppResult<Page<PointLedgerEntry>> {
            pageCalls += page
            return AppResult.Success(Page(ledger, PageMeta(page, 20, ledger.size, 1)))
        }
    }

    private class FailingLearnerLanguageStore(initial: LearnerLanguage) : LearnerLanguageStore {
        private val mutable = MutableStateFlow(initial)
        var attempts = 0
            private set

        override val language: StateFlow<LearnerLanguage> = mutable.asStateFlow()

        override fun setLanguage(value: LearnerLanguage): Boolean {
            attempts++
            return false
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
        fun ledgerEntry(id: String, delta: Int, balanceAfter: Int) = PointLedgerEntry(
            id, "id-a", PointLedgerType.ANSWER_REWARD, delta, balanceAfter, "a1", null,
            Instant.parse("2026-07-30T08:00:00Z"),
        )
    }
}
