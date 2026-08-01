package com.pointquest.android.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.User
import com.pointquest.android.data.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val showLogoutConfirmation: Boolean = false,
    val loggingOut: Boolean = false,
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    sessionState: SessionState,
    private val scopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = mutableUiState

    init {
        scope.launch {
            sessionState.status.collect { status ->
                mutableUiState.value = mutableUiState.value.copy(
                    user = (status as? SessionStatus.SignedIn)?.user,
                )
            }
        }
    }

    fun requestLogout() {
        if (mutableUiState.value.loggingOut) return
        mutableUiState.value = mutableUiState.value.copy(showLogoutConfirmation = true)
    }

    fun dismissLogout() {
        if (mutableUiState.value.loggingOut) return
        mutableUiState.value = mutableUiState.value.copy(showLogoutConfirmation = false)
    }

    fun confirmLogout(): Job? {
        if (!mutableUiState.value.showLogoutConfirmation || mutableUiState.value.loggingOut) return null
        mutableUiState.value = mutableUiState.value.copy(loggingOut = true)
        return scope.launch {
            try {
                authRepository.logout()
            } finally {
                mutableUiState.value = mutableUiState.value.copy(
                    showLogoutConfirmation = false,
                    loggingOut = false,
                )
            }
        }
    }

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope
}
