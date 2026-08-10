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
        val observerFailure = notifyActiveSessionObservers(session)
        mutableStatus.value = SessionStatus.SignedIn(session.user)
        observerFailure?.let { throw it }
    }

    internal fun clear() = synchronized(observerLock) {
        mutableActive.value = null
        val observerFailure = notifyActiveSessionObservers(null)
        mutableStatus.value = SessionStatus.SignedOut
        observerFailure?.let { throw it }
    }

    internal fun observeActiveSession(observer: (ActiveSession?) -> Unit) = synchronized(observerLock) {
        observer(mutableActive.value)
        activeSessionObservers += observer
    }

    private fun notifyActiveSessionObservers(session: ActiveSession?): Throwable? {
        var firstFailure: Throwable? = null
        activeSessionObservers.toList().forEach { observer ->
            try {
                observer(session)
            } catch (failure: Throwable) {
                if (firstFailure == null) {
                    firstFailure = failure
                } else if (firstFailure !== failure) {
                    firstFailure.addSuppressed(failure)
                }
            }
        }
        return firstFailure
    }
}
