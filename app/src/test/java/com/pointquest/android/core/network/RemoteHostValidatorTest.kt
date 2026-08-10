package com.pointquest.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteHostValidatorTest {
    @Test
    fun trimsAndNormalizesHttpsOrigin() {
        assertEquals(
            RemoteHostValidation.Valid("https://api.example.test/"),
            RemoteHostValidator(allowHttp = false).validate(" https://api.example.test "),
        )
    }

    @Test
    fun acceptsHttpWhenExplicitlyAllowed() {
        assertEquals(
            RemoteHostValidation.Valid("http://192.168.1.10:3000/"),
            RemoteHostValidator(allowHttp = true).validate("http://192.168.1.10:3000/"),
        )
    }

    @Test
    fun rejectsHttpWhenHttpsIsRequired() {
        assertEquals(
            RemoteHostErrorCode.HTTPS_REQUIRED,
            (RemoteHostValidator(allowHttp = false).validate("http://dev.example.test/")
                as RemoteHostValidation.Invalid).code,
        )
    }

    @Test
    fun rejectsMissingHost() {
        assertInvalid(RemoteHostErrorCode.REQUIRED, " ")
    }

    @Test
    fun rejectsInvalidProtocol() {
        assertInvalid(RemoteHostErrorCode.INVALID_FORMAT, "ftp://api.example.test/")
    }

    @Test
    fun rejectsNonRootPaths() {
        assertInvalid(RemoteHostErrorCode.ROOT_PATH_ONLY, "https://api.example.test/path")
        assertInvalid(RemoteHostErrorCode.ROOT_PATH_ONLY, "https://api.example.test/api/v1/")
    }

    @Test
    fun rejectsUserInfoQueryAndFragment() {
        assertInvalid(RemoteHostErrorCode.INVALID_FORMAT, "https://user:secret@api.example.test/")
        assertInvalid(RemoteHostErrorCode.INVALID_FORMAT, "https://api.example.test/?token=secret")
        assertInvalid(RemoteHostErrorCode.INVALID_FORMAT, "https://api.example.test/#fragment")
    }

    @Test
    fun rejectsInvalidPorts() {
        assertInvalid(RemoteHostErrorCode.INVALID_FORMAT, "https://api.example.test:65536/")
        assertInvalid(RemoteHostErrorCode.INVALID_FORMAT, "https://api.example.test:not-a-port/")
    }

    @Test
    fun acceptsIpv6RootAddress() {
        assertEquals(
            RemoteHostValidation.Valid("https://[::1]/"),
            RemoteHostValidator(allowHttp = false).validate("https://[::1]"),
        )
    }

    private fun assertInvalid(code: RemoteHostErrorCode, raw: String) {
        assertEquals(code, (RemoteHostValidator(allowHttp = true).validate(raw) as RemoteHostValidation.Invalid).code)
    }
}
