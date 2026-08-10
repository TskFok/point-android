package com.pointquest.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointquest.android.R
import com.pointquest.android.core.network.RemoteHostApplyResult
import com.pointquest.android.core.network.RemoteHostErrorCode
import com.pointquest.android.core.network.RemoteHostStore
import com.pointquest.android.core.ui.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RemoteHostUiState(
    val activeHost: String,
    val draftHost: String,
    val error: UiText? = null,
    val message: UiText? = null,
    val applying: Boolean = false,
)

class RemoteHostViewModel(
    private val store: RemoteHostStore,
    private val scopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        RemoteHostUiState(
            activeHost = store.currentHost,
            draftHost = store.currentHost,
        ),
    )

    val uiState: StateFlow<RemoteHostUiState> = mutableUiState

    fun updateHost(value: String) {
        if (mutableUiState.value.applying) return
        mutableUiState.value = mutableUiState.value.copy(
            draftHost = value,
            error = null,
            message = null,
        )
    }

    fun apply(): Job? {
        val current = mutableUiState.value
        if (current.applying) return null

        mutableUiState.value = current.copy(
            error = null,
            message = null,
            applying = true,
        )
        return scope.launch {
            val result = withContext(Dispatchers.IO) {
                store.apply(current.draftHost)
            }
            mutableUiState.value = when (result) {
                is RemoteHostApplyResult.Applied -> mutableUiState.value.copy(
                    activeHost = result.host,
                    draftHost = result.host,
                    error = null,
                    message = UiText.Resource(R.string.remote_host_apply_success),
                    applying = false,
                )
                is RemoteHostApplyResult.Rejected -> mutableUiState.value.copy(
                    error = errorFor(result.code),
                    applying = false,
                )
                RemoteHostApplyResult.PersistenceFailed -> mutableUiState.value.copy(
                    error = UiText.Resource(R.string.remote_host_error_persistence_failed),
                    applying = false,
                )
            }
        }
    }

    fun requireAppliedForAuthentication(): Boolean {
        val current = mutableUiState.value
        if (!current.applying && current.draftHost == current.activeHost) return true
        mutableUiState.value = current.copy(
            error = UiText.Resource(R.string.remote_host_apply_before_login),
        )
        return false
    }

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private fun errorFor(code: RemoteHostErrorCode): UiText.Resource = UiText.Resource(
        when (code) {
            RemoteHostErrorCode.REQUIRED -> R.string.remote_host_error_required
            RemoteHostErrorCode.INVALID_FORMAT -> R.string.remote_host_error_invalid_format
            RemoteHostErrorCode.ROOT_PATH_ONLY -> R.string.remote_host_error_root_path
            RemoteHostErrorCode.HTTPS_REQUIRED -> R.string.remote_host_error_https_required
        },
    )
}
