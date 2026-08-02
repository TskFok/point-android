package com.pointquest.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppMetadataTest {
    @Test
    fun appNameAndVersionMatchReleaseIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("Point Quest", context.getString(R.string.app_name))
        assertEquals("0.1.0", BuildConfig.VERSION_NAME)
    }
}
