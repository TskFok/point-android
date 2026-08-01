package com.pointquest.android.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

class ViewModelFactory<VM : ViewModel>(
    private val initializer: (CreationExtras) -> VM,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val viewModel = initializer(extras)
        require(modelClass.isInstance(viewModel)) {
            "Factory created ${viewModel::class.java.name} for ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }
}
