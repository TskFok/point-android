package com.pointquest.android.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pointquest.android.R
import com.pointquest.android.core.network.AppError

sealed interface UiText {
    data class Resource(
        @StringRes val id: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : UiText

    data class Dynamic(val value: String) : UiText
}

object UiErrorMapper {
    fun map(error: AppError): UiText = when (error.code) {
        "FORBIDDEN" -> UiText.Resource(R.string.error_forbidden)
        else -> error.requestId?.takeIf(String::isNotBlank)?.let { requestId ->
            UiText.Resource(
                R.string.error_with_request_id,
                listOf(error.message, requestId),
            )
        } ?: UiText.Dynamic(error.message)
    }
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.Resource -> stringResource(id, *formatArgs.toTypedArray())
}
