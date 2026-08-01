package com.pointquest.android.core.ui

import com.pointquest.android.R
import com.pointquest.android.core.network.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

class UiErrorMapperTest {
    @Test
    fun forbiddenCodeAlwaysUsesFixedPermissionCopy() {
        val error = AppError(
            httpStatus = 403,
            code = "FORBIDDEN",
            message = "server wording must not leak",
            requestId = "request-forbidden",
        )

        assertEquals(UiText.Resource(R.string.error_forbidden), UiErrorMapper.map(error))
    }

    @Test
    fun unknownCodeUsesServerMessageAndRequestIdWhenPresent() {
        val error = AppError(
            httpStatus = 409,
            code = "BUSINESS_RULE_CHANGED",
            message = "当前操作暂不可用",
            requestId = "request-42",
        )

        assertEquals(
            UiText.Resource(
                R.string.error_with_request_id,
                listOf("当前操作暂不可用", "request-42"),
            ),
            UiErrorMapper.map(error),
        )
    }

    @Test
    fun messageContentNeverSelectsAStableCodeBranch() {
        val error = AppError(
            httpStatus = 500,
            code = "UNKNOWN",
            message = "FORBIDDEN",
            requestId = null,
        )

        assertEquals(UiText.Dynamic("FORBIDDEN"), UiErrorMapper.map(error))
    }

    @Test
    fun blankRequestIdIsNotRenderedAsDiagnosticInformation() {
        val error = AppError(
            httpStatus = 500,
            code = "UNKNOWN",
            message = "服务暂时不可用",
            requestId = "   ",
        )

        assertEquals(UiText.Dynamic("服务暂时不可用"), UiErrorMapper.map(error))
    }
}
