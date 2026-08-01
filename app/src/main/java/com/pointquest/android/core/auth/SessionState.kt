package com.pointquest.android.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionState {
    private val mutableActive = MutableStateFlow<ActiveSession?>(null)

    val active: StateFlow<ActiveSession?> = mutableActive.asStateFlow()

    internal fun publish(session: ActiveSession) {
        mutableActive.value = session
    }

    internal fun clear() {
        mutableActive.value = null
    }
}
