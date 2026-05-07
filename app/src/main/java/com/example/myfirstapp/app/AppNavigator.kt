package com.example.myfirstapp.app
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.example.myfirstapp.core.ui.navigation.AppNavigator as FeatureNavigator
import kotlin.reflect.KClass

class AppNavigator(
    private val state: AppNavigationState,
    private val transientRouteTypes: Set<KClass<out NavKey>> = emptySet()
) : FeatureNavigator {
    var blocksBack: Boolean by mutableStateOf(false)
        private set

    override fun navigate(route: NavKey) {
        if (route in state.topLevelRoutes) {
            navigateToTopLevel(route)
        } else if (route::class in transientRouteTypes) {
            state.transientStack.apply {
                remove(route)
                add(route)
            }
        } else {
            state.currentStack.apply {
                remove(route)
                add(route)
            }
        }
    }

    override fun goBack(): Boolean {
        if (blocksBack) return true

        if (state.transientStack.isNotEmpty()) {
            state.transientStack.removeLastOrNull()
            return true
        }

        val currentStack = state.currentStack
        if (currentStack.last() != state.topLevelRoute) {
            currentStack.removeLastOrNull()
            return true
        }
        if (state.topLevelRoute != state.startRoute) {
            state.topLevelStack.removeLastOrNull()
            return true
        }
        return false
    }

    fun closeCurrentEntry(): Boolean {
        if (blocksBack) return true

        if (state.transientStack.isNotEmpty()) {
            state.transientStack.removeLastOrNull()
            return true
        }

        val currentStack = state.currentStack
        if (currentStack.last() == state.topLevelRoute) return false

        currentStack.removeLastOrNull()
        return true
    }

    override fun setBackBlocked(blocked: Boolean) {
        blocksBack = blocked
    }

    private fun navigateToTopLevel(route: NavKey) {
        if (route == state.topLevelRoute) {
            clearTransientRoutes()
            state.currentStack.run {
                if (size > 1) subList(1, size).clear()
            }
        } else {
            clearTransientRoutes()
            state.topLevelRoute = route
        }
    }

    private fun clearTransientRoutes() {
        state.transientStack.clear()
        state.backStacks.values.forEach { stack ->
            for (index in stack.lastIndex downTo 1) {
                if (stack[index]::class in transientRouteTypes) {
                    stack.removeAt(index)
                }
            }
        }
    }
}
