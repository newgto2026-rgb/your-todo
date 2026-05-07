@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myfirstapp.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.example.myfirstapp.core.ui.navigation.BottomSheetRouteMetadata

internal data class BottomSheetScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val properties: ModalBottomSheetProperties,
    private val onBack: () -> Unit
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        val lifecycleOwner = rememberLifecycleOwner()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            modifier = Modifier.wrapContentHeight(),
            sheetState = sheetState,
            onDismissRequest = onBack,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0) },
            properties = properties
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                entry.Content()
            }
        }
    }
}

class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        return calculateBottomSheetScene(entries = entries, onBack = onBack)
    }
}

internal fun <T : Any> calculateBottomSheetScene(
    entries: List<NavEntry<T>>,
    onBack: () -> Unit
): BottomSheetScene<T>? {
    val entry = entries.lastOrNull() ?: return null
    val properties = entry.metadata[BottomSheetRouteMetadata.BottomSheetKey] ?: return null
    return BottomSheetScene(
        key = entry.contentKey,
        previousEntries = entries.dropLast(1),
        overlaidEntries = entries.dropLast(1),
        entry = entry,
        properties = properties,
        onBack = onBack
    )
}
