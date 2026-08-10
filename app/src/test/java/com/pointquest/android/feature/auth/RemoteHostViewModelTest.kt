package com.pointquest.android.feature.auth

import com.pointquest.android.R
import com.pointquest.android.core.network.RemoteHostPersistence
import com.pointquest.android.core.network.RemoteHostStore
import com.pointquest.android.core.network.RemoteHostValidator
import com.pointquest.android.core.ui.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteHostViewModelTest {
    @Test
    fun appliesEditedHostAndAllowsLoginAfterItHasBeenApplied() = runTest {
        val viewModel = RemoteHostViewModel(store(), backgroundScope)

        viewModel.updateHost("https://new.example.test")

        assertTrue(viewModel.uiState.value.draftHost != viewModel.uiState.value.activeHost)
        viewModel.apply()!!.join()
        assertEquals("https://new.example.test/", viewModel.uiState.value.activeHost)
        assertTrue(viewModel.requireAppliedForAuthentication())
    }

    @Test
    fun invalidApplyKeepsActiveHostAndShowsMappedError() = runTest {
        val viewModel = RemoteHostViewModel(store(), backgroundScope)

        viewModel.updateHost("https://new.example.test/api")
        viewModel.apply()!!.join()

        assertEquals("https://default.example.test/", viewModel.uiState.value.activeHost)
        assertEquals("https://new.example.test/api", viewModel.uiState.value.draftHost)
        assertEquals(UiText.Resource(R.string.remote_host_error_root_path), viewModel.uiState.value.error)
        assertFalse(viewModel.requireAppliedForAuthentication())
    }

    @Test
    fun unappliedDraftPreventsLoginAndEditingClearsPreviousFeedback() = runTest {
        val viewModel = RemoteHostViewModel(store(), backgroundScope)
        viewModel.updateHost("https://new.example.test/api")
        viewModel.apply()!!.join()

        viewModel.updateHost("https://new.example.test")

        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.requireAppliedForAuthentication())
        assertEquals(
            UiText.Resource(R.string.remote_host_apply_before_login),
            viewModel.uiState.value.error,
        )
    }

    @Test
    fun duplicateApplyIsRejectedWhileAnApplyIsPending() = runTest {
        val viewModel = RemoteHostViewModel(store(), backgroundScope)
        viewModel.updateHost("https://new.example.test")

        val first = viewModel.apply()
        val duplicate = viewModel.apply()

        assertNotNull(first)
        assertNull(duplicate)
        runCurrent()
        first!!.join()
        assertFalse(viewModel.uiState.value.applying)
    }

    @Test
    fun authenticationIsBlockedWhileCurrentHostIsBeingApplied() = runTest {
        val viewModel = RemoteHostViewModel(store(), backgroundScope)

        val applyJob = viewModel.apply()

        assertTrue(viewModel.uiState.value.applying)
        assertFalse(viewModel.requireAppliedForAuthentication())
        applyJob!!.join()
    }

    @Test
    fun successfulApplyClearsLoginPreconditionErrorSetWhileApplying() = runTest {
        val viewModel = RemoteHostViewModel(store(), backgroundScope)
        viewModel.updateHost("https://new.example.test")

        val applyJob = viewModel.apply()
        assertFalse(viewModel.requireAppliedForAuthentication())
        assertEquals(
            UiText.Resource(R.string.remote_host_apply_before_login),
            viewModel.uiState.value.error,
        )

        applyJob!!.join()

        assertEquals("https://new.example.test/", viewModel.uiState.value.activeHost)
        assertNull(viewModel.uiState.value.error)
        assertEquals(UiText.Resource(R.string.remote_host_apply_success), viewModel.uiState.value.message)
    }

    private fun store() = RemoteHostStore(
        defaultHost = "https://default.example.test/",
        persistence = MemoryPersistence(),
        validator = RemoteHostValidator(allowHttp = true),
    )

    private class MemoryPersistence : RemoteHostPersistence {
        override fun read(): String? = null

        override fun write(value: String) = Unit
    }
}
