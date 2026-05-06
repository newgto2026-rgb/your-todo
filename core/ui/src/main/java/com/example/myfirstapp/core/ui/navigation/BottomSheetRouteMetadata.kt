package com.example.myfirstapp.core.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata

@OptIn(ExperimentalMaterial3Api::class)
object BottomSheetRouteMetadata {
    object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>

    fun bottomSheet(): Map<String, Any> = metadata {
        put(BottomSheetKey, ModalBottomSheetProperties())
    }
}
