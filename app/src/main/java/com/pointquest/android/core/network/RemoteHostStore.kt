package com.pointquest.android.core.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface RemoteHostPersistence {
    fun read(): String?

    fun write(value: String)
}

class SharedPreferencesRemoteHostPersistence(
    context: Context,
) : RemoteHostPersistence {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(REMOTE_HOST_KEY, null)

    override fun write(value: String) {
        check(preferences.edit().putString(REMOTE_HOST_KEY, value).commit()) {
            "Unable to persist remote host"
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "point_quest_settings"
        const val REMOTE_HOST_KEY = "remote_host"
    }
}

class RemoteHostStore(
    defaultHost: String,
    private val persistence: RemoteHostPersistence,
    private val validator: RemoteHostValidator,
) {
    private val defaultValue = validatedDefault(defaultHost)
    private val applyLock = Any()
    private val mutableHost = MutableStateFlow(initialHost())

    val currentHost: String
        get() = mutableHost.value

    val hostFlow: StateFlow<String> = mutableHost.asStateFlow()

    fun apply(raw: String): RemoteHostApplyResult {
        return when (val validation = validator.validate(raw)) {
            is RemoteHostValidation.Invalid -> RemoteHostApplyResult.Rejected(validation.code)
            is RemoteHostValidation.Valid -> synchronized(applyLock) {
                try {
                    persistence.write(validation.normalized)
                    mutableHost.value = validation.normalized
                    RemoteHostApplyResult.Applied(validation.normalized)
                } catch (_: Exception) {
                    RemoteHostApplyResult.PersistenceFailed
                }
            }
        }
    }

    private fun initialHost(): String = try {
        persistence.read()
            ?.let { validator.validate(it) }
            ?.let { validation ->
                when (validation) {
                    is RemoteHostValidation.Valid -> validation.normalized
                    is RemoteHostValidation.Invalid -> null
                }
            }
            ?: defaultValue
    } catch (_: Exception) {
        defaultValue
    }

    private fun validatedDefault(raw: String): String = when (val validation = validator.validate(raw)) {
        is RemoteHostValidation.Valid -> validation.normalized
        is RemoteHostValidation.Invalid -> error("Default remote host is invalid: ${validation.code}")
    }
}

sealed interface RemoteHostApplyResult {
    data class Applied(val host: String) : RemoteHostApplyResult

    data class Rejected(val code: RemoteHostErrorCode) : RemoteHostApplyResult

    data object PersistenceFailed : RemoteHostApplyResult
}
