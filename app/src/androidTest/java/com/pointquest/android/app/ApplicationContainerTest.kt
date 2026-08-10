package com.pointquest.android.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test
    fun containerExposesOneStableRemoteHost() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as PointQuestApplication

        val container = application.container

        assertTrue(container.remoteHostStore.currentHost.isNotBlank())
        assertEquals(container.remoteHostStore.currentHost, container.remoteHostStore.currentHost)
    }
}
