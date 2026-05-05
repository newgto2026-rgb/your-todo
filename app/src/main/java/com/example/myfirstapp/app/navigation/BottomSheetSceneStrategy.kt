@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myfirstapp.app.navigation

import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.example.myfirstapp.core.ui.navigation.BottomSheetRouteMetadata

private const val TAG = "NavScopeTrace"

private class BottomSheetScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val onBack: () -> Unit
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        LaunchedEffect(Unit) {
            sheetState.show()
        }
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                Log.d(TAG, "sheet onDismissRequest key=${entry.contentKey}")
                onBack()
            },
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = true
            )
        ) {
            entry.Content()
        }
    }
}

class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.lastOrNull() ?: return null
        val isBottomSheet = entry.metadata[BottomSheetRouteMetadata.BottomSheetKey] != null
        Log.d(
            TAG,
            "calculateScene size=${entries.size} topKey=${entry.contentKey} isBottomSheet=$isBottomSheet"
        )
        if (!isBottomSheet) return null
        return BottomSheetScene(
            key = entry.contentKey,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = entry,
            onBack = onBack
        )
    }
}
