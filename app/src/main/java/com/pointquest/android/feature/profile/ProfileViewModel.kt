package com.pointquest.android.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.app.AppDataSync
import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.auth.SessionStatus
import com.pointquest.android.core.model.LearnerLanguage
import com.pointquest.android.core.model.User
import com.pointquest.android.core.ui.UiText
import com.pointquest.android.data.auth.AuthRepository
import com.pointquest.android.data.preferences.LearnerLanguageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val language: LearnerLanguage = LearnerLanguage.ALL,
    val languagePersistenceError: UiText? = null,
    val showLogoutConfirmation: Boolean = false,
    val loggingOut: Boolean = false,
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    sessionState: SessionState,
    private val learnerLanguageStore: LearnerLanguageStore,
    private val scopeOverride: CoroutineScope? = null,
    private val appDataSync: AppDataSync? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        ProfileUiState(language = learnerLanguageStore.language.value),
    )
    val uiState: StateFlow<ProfileUiState> = mutableUiState

    init {
        scope.launch {
            learnerLanguageStore.language.collect { language ->
                mutableUiState.value = mutableUiState.value.copy(
                    language = language,
                    languagePersistenceError = null,
                )
            }
        }
        scope.launch {
            val syncState = appDataSync?.state
            if (syncState == null) {
                sessionState.status.collect { status ->
                    mutableUiState.value = mutableUiState.value.copy(
                        user = (status as? SessionStatus.SignedIn)?.user,
                    )
                }
            } else {
                sessionState.status.combine(syncState) { status, sync -> status to sync }
                    .collect { (status, sync) ->
                        val user = (status as? SessionStatus.SignedIn)?.user
                        val balance = sync.balance.takeIf { sync.session?.userId == user?.id }
                        mutableUiState.value = mutableUiState.value.copy(
                            user = user?.let { current ->
                                balance?.let { current.copy(pointsBalance = it) } ?: current
                            },
                        )
                    }
            }
        }
    }

    fun setLanguage(language: LearnerLanguage) {
        if (language == mutableUiState.value.language) {
            mutableUiState.value = mutableUiState.value.copy(languagePersistenceError = null)
            return
        }
        if (!learnerLanguageStore.setLanguage(language)) {
            mutableUiState.value = mutableUiState.value.copy(
                languagePersistenceError = UiText.Resource(R.string.profile_language_save_failed),
            )
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
