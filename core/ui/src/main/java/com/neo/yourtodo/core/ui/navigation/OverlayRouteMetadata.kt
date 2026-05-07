package com.neo.yourtodo.core.ui.navigation

import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata

object OverlayRouteMetadata {
    object OverlayKey : NavMetadataKey<Boolean>

    fun overlay(): Map<String, Any> = metadata { put(OverlayKey, true) }
}
