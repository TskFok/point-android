package com.pointquest.android.core.auth

import com.pointquest.android.core.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionStatus {
    data object Restoring : SessionStatus

    data object SignedOut : SessionStatus

    data class SignedIn(val user: User) : SessionStatus
}

class SessionState {
    private val mutableActive = MutableStateFlow<ActiveSession?>(null)
    private val mutableStatus = MutableStateFlow<SessionStatus>(SessionStatus.Restoring)
    private val observerLock = Any()
    private val activeSessionObservers = LinkedHashSet<(ActiveSession?) -> Unit>()

    val active: StateFlow<ActiveSession?> = mutableActive.asStateFlow()
    val status: StateFlow<SessionStatus> = mutableStatus.asStateFlow()

    internal fun publish(session: ActiveSession) = synchronized(observerLock) {
        mutableActive.value = session
        activeSessionObservers.forEach { it(session) }
        mutableStatus.value = SessionStatus.SignedIn(session.user)
    }

    internal fun clear() = synchronized(observerLock) {
        mutableActive.value = null
        activeSessionObservers.forEach { it(null) }
        mutableStatus.value = SessionStatus.SignedOut
    }

    internal fun observeActiveSession(observer: (ActiveSession?) -> Unit) = synchronized(observerLock) {
        activeSessionObservers += observer
        observer(mutableActive.value)
    }
}
