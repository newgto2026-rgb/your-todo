package com.neo.yourtodo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.savedstate.compose.LocalSavedStateRegistryOwner

@Composable
fun <T : Any> rememberRetainedSaveableStateHolderNavEntryDecorator(
    saveableStateHolder: SaveableStateHolder = rememberSaveableStateHolder()
): RetainedSaveableStateHolderNavEntryDecorator<T> =
    remember(saveableStateHolder) {
        RetainedSaveableStateHolderNavEntryDecorator(saveableStateHolder)
    }

class RetainedSaveableStateHolderNavEntryDecorator<T : Any>(
    private val saveableStateHolder: SaveableStateHolder
) {
    @Composable
    fun Decorate(contentKey: Any, content: @Composable () -> Unit) {
        key(contentKey) {
            saveableStateHolder.SaveableStateProvider(contentKey) {
                content()
            }
        }
    }

    fun clearKey(contentKey: Any) {
        saveableStateHolder.removeState(contentKey)
    }
}

@Composable
fun <T : Any> rememberRetainedViewModelStoreNavEntryDecorator(
    viewModelStoreOwner: ViewModelStoreOwner =
        checkNotNull(LocalViewModelStoreOwner.current) {
            "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
        }
): RetainedViewModelStoreNavEntryDecorator<T> {
    val viewModelStoreProvider = rememberViewModelStoreProvider(parent = viewModelStoreOwner)
    return remember(viewModelStoreOwner, viewModelStoreProvider) {
        RetainedViewModelStoreNavEntryDecorator(viewModelStoreProvider)
    }
}

class RetainedViewModelStoreNavEntryDecorator<T : Any>(
    private val viewModelStoreProvider: ViewModelStoreProvider
) {
    @Composable
    fun Decorate(contentKey: Any, content: @Composable () -> Unit) {
        key(contentKey) {
            val owner = rememberViewModelStoreOwner(
                viewModelStoreProvider,
                contentKey,
                savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
            )
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                content()
                AppNavigationIdentityProbe.publishViewModels(
                    contentKey = contentKey,
                    viewModelStore = owner.viewModelStore
                )
            }
        }
    }

    fun clearKey(contentKey: Any) {
        viewModelStoreProvider.clearKey(contentKey)
    }
}
