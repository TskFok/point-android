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

    val active: StateFlow<ActiveSession?> = mutableActive.asStateFlow()
    val status: StateFlow<SessionStatus> = mutableStatus.asStateFlow()

    internal fun publish(session: ActiveSession) {
        mutableActive.value = session
        mutableStatus.value = SessionStatus.SignedIn(session.user)
    }

    internal fun clear() {
        mutableActive.value = null
        mutableStatus.value = SessionStatus.SignedOut
    }
}
