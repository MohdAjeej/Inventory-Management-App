package com.b2binventory.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.b2binventory.app.InventoryApp
import com.b2binventory.app.data.remote.Me
import com.b2binventory.app.di.AppContainer

/** The signed-in member, used to show or hide actions. The server enforces every permission anyway. */
val LocalMe = staticCompositionLocalOf<Me?> { null }

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as InventoryApp).container

/** Creates a ViewModel with access to the app container and the navigation arguments. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    noinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory {
        initializer { create(container, createSavedStateHandle()) }
    })
}

/** Simple load state shared by screens that fetch one thing. */
data class Loadable<T>(val data: T? = null, val loading: Boolean = true, val error: String? = null)
