package com.neo.yourtodo.app.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SceneStrategyScope
import com.neo.yourtodo.core.ui.navigation.BottomSheetRouteMetadata
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SceneStrategyTest {

    @Test
    fun bottomSheetSceneStrategy_ignoresStackWhenLastEntryIsNotBottomSheet() {
        val inactiveSheet = navEntry(
            route = InactiveSheetRoute,
            metadata = BottomSheetRouteMetadata.bottomSheet()
        )
        val activeBase = navEntry(ActiveRoute)
        val strategy = BottomSheetSceneStrategy<NavKey>()

        val scene = with(strategy) {
            SceneStrategyScope<NavKey>().calculateScene(listOf(inactiveSheet, activeBase))
        }

        assertThat(scene).isNull()
    }

    @Test
    fun bottomSheetSceneStrategy_overlaysPreviousEntries() {
        val activeBase = navEntry(ActiveRoute)
        val activeSheet = navEntry(
            route = ActiveSheetRoute,
            metadata = BottomSheetRouteMetadata.bottomSheet()
        )
        val strategy = BottomSheetSceneStrategy<NavKey>()

        val scene = with(strategy) {
            SceneStrategyScope<NavKey>().calculateScene(
                listOf(activeBase, activeSheet)
            )
        }

        assertThat(scene).isInstanceOf(OverlayScene::class.java)
        val overlayScene = scene as OverlayScene<NavKey>
        assertThat(overlayScene.entries).containsExactly(activeSheet)
        assertThat(overlayScene.previousEntries).containsExactly(activeBase)
        assertThat(overlayScene.overlaidEntries).containsExactly(activeBase)
    }

    private fun navEntry(
        route: NavKey,
        metadata: Map<String, Any> = emptyMap()
    ): NavEntry<NavKey> = NavEntry(
        key = route,
        metadata = metadata
    ) {}

    private data object ActiveRoute : NavKey
    private data object ActiveSheetRoute : NavKey
    private data object InactiveSheetRoute : NavKey
}
