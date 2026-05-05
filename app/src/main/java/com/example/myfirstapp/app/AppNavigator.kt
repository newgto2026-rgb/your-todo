package com.example.myfirstapp.app

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.example.myfirstapp.core.ui.navigation.AppNavigator as FeatureNavigator

class AppNavigator(
    private val state: AppNavigationState
) : FeatureNavigator {
    private companion object {
        const val TAG = "NavScopeTrace"
    }

    var blocksBack: Boolean by mutableStateOf(false)
        private set

    override fun navigate(route: NavKey) {
        Log.d(
            TAG,
            "navigate route=$route topLevel=${state.topLevelRoute} stackBefore=${state.currentStack.toList()}"
        )
        if (route in state.topLevelRoutes) {
            navigateToTopLevel(route)
        } else {
            state.currentStack.add(route)
        }
        Log.d(TAG, "navigate done stackAfter=${state.currentStack.toList()}")
    }

    override fun goBack(): Boolean {
        if (blocksBack) {
            Log.d(TAG, "goBack blocked=true stack=${state.currentStack.toList()}")
            return true
        }

        val currentStack = state.currentStack
        if (currentStack.last() != state.topLevelRoute) {
            val removed = currentStack.removeLastOrNull()
            Log.d(
                TAG,
                "goBack popped=$removed topLevel=${state.topLevelRoute} stackAfter=${state.currentStack.toList()}"
            )
            return true
        }
        Log.d(
            TAG,
            "goBack no-op at root topLevel=${state.topLevelRoute} stack=${state.currentStack.toList()}"
        )
        return false
    }

    override fun setBackBlocked(blocked: Boolean) {
        blocksBack = blocked
    }

    private fun navigateToTopLevel(route: NavKey) {
        if (route == state.topLevelRoute) return

        Log.d(
            TAG,
            "navigateToTopLevel from=${state.topLevelRoute} to=$route stackBefore=${state.currentStack.toList()}"
        )
        state.topLevelHistory = emptyList()
        state.topLevelRoute = route
        Log.d(TAG, "navigateToTopLevel done topLevel=${state.topLevelRoute}")
    }
}
