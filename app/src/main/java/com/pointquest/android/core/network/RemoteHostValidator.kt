package com.pointquest.android.core.network

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class RemoteHostValidator(
    private val allowHttp: Boolean,
) {
    fun validate(raw: String): RemoteHostValidation {
        val value = raw.trim()
        if (value.isEmpty()) return RemoteHostValidation.Invalid(RemoteHostErrorCode.REQUIRED)

        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return RemoteHostValidation.Invalid(RemoteHostErrorCode.INVALID_FORMAT)
        }

        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            return RemoteHostValidation.Invalid(RemoteHostErrorCode.INVALID_FORMAT)
        }
        if (uri.rawPath != null && uri.rawPath != "" && uri.rawPath != "/") {
            return RemoteHostValidation.Invalid(RemoteHostErrorCode.ROOT_PATH_ONLY)
        }

        val url = value.toHttpUrlOrNull()
            ?: return RemoteHostValidation.Invalid(RemoteHostErrorCode.INVALID_FORMAT)
        if (url.scheme != "http" && url.scheme != "https") {
            return RemoteHostValidation.Invalid(RemoteHostErrorCode.INVALID_FORMAT)
        }
        if (url.scheme == "http" && !allowHttp) {
            return RemoteHostValidation.Invalid(RemoteHostErrorCode.HTTPS_REQUIRED)
        }
        if (url.host.isEmpty()) {
            return RemoteHostValidation.Invalid(RemoteHostErrorCode.INVALID_FORMAT)
        }

        return RemoteHostValidation.Valid(url.newBuilder().encodedPath("/").build().toString())
    }
}

sealed interface RemoteHostValidation {
    data class Valid(val normalized: String) : RemoteHostValidation

    data class Invalid(val code: RemoteHostErrorCode) : RemoteHostValidation
}

enum class RemoteHostErrorCode {
    REQUIRED,
    INVALID_FORMAT,
    ROOT_PATH_ONLY,
    HTTPS_REQUIRED,
}
