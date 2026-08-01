package com.pointquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BuildConfigTest {
    @Test
    fun debugBaseUrlUsesServiceRootWithoutVersionPath() {
        assertEquals("http://10.0.2.2:3000/", BuildConfig.API_BASE_URL)
        assertFalse(BuildConfig.API_BASE_URL.contains("/api/v1"))
    }
}
