package com.pointquest.android.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import org.junit.Assert.assertSame
import org.junit.Test

class ViewModelFactoryTest {
    @Test
    fun factoryCreatesTheRequestedViewModelFromCreationExtras() {
        val expected = SampleViewModel()
        val factory = ViewModelFactory { extras ->
            assertSame(CreationExtras.Empty, extras)
            expected
        }

        val actual = factory.create(SampleViewModel::class.java, CreationExtras.Empty)

        assertSame(expected, actual)
    }

    private class SampleViewModel : ViewModel()
}
