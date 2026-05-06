package com.example.myfirstapp.app
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.example.myfirstapp.core.ui.navigation.AppNavigator as FeatureNavigator

class AppNavigator(
    private val state: AppNavigationState
) : FeatureNavigator {
    var blocksBack: Boolean by mutableStateOf(false)
        private set

    override fun navigate(route: NavKey) {
        if (route in state.topLevelRoutes) {
            navigateToTopLevel(route)
        } else {
            state.currentStack.add(route)
        }
    }

    override fun goBack(): Boolean {
        if (blocksBack) return true

        val currentStack = state.currentStack
        if (currentStack.last() != state.topLevelRoute) {
            currentStack.removeLastOrNull()
            return true
        }
        return false
    }

    override fun setBackBlocked(blocked: Boolean) {
        blocksBack = blocked
    }

    private fun navigateToTopLevel(route: NavKey) {
        if (route == state.topLevelRoute) return

        state.topLevelHistory = emptyList()
        state.topLevelRoute = route
    }
}
