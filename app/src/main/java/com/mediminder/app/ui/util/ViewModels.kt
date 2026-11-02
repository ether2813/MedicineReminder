package com.mediminder.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mediminder.app.MediApp

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (MediApp) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as MediApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app) } })
}
