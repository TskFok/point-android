package com.pointquest.android.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationContainerTest {
    @Test
    fun manifestApplicationOwnsOneStableContainer() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as PointQuestApplication

        assertSame(application.container, application.container)
    }
}
