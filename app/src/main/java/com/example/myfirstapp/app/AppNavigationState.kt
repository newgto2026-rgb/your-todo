package com.example.myfirstapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import kotlinx.serialization.builtins.ListSerializer

@Composable
fun rememberAppNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>
): AppNavigationState {
    val topLevelRoute = rememberSerializable(
        startRoute,
        topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer())
    ) {
        mutableStateOf(startRoute)
    }
    val topLevelHistory = rememberSerializable(
        startRoute,
        topLevelRoutes,
        serializer = MutableStateSerializer(ListSerializer(NavKeySerializer()))
    ) {
        mutableStateOf(emptyList())
    }
    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        AppNavigationState(
            startRoute = startRoute,
            topLevelRoutes = topLevelRoutes,
            topLevelRoute = topLevelRoute,
            topLevelHistory = topLevelHistory,
            backStacks = backStacks
        )
    }
}

class AppNavigationState(
    val startRoute: NavKey,
    val topLevelRoutes: Set<NavKey>,
    topLevelRoute: MutableState<NavKey>,
    topLevelHistory: MutableState<List<NavKey>>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>
) {
    var topLevelRoute: NavKey by topLevelRoute
    var topLevelHistory: List<NavKey> by topLevelHistory

    val currentRoute: NavKey
        get() = currentStack.last()

    val currentStack: NavBackStack<NavKey>
        get() = requireNotNull(backStacks[topLevelRoute]) {
            "Stack for $topLevelRoute not found"
        }
}

@Composable
fun AppNavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): List<NavEntry<NavKey>> {
    val entryDecorators: List<NavEntryDecorator<NavKey>> = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>()
    )
    val decoratedEntries = LinkedHashMap<NavKey, List<NavEntry<NavKey>>>(backStacks.size)
    topLevelRoutes
        .toList()
        .sortedBy { it.toString() }
        .forEach { route ->
            val stack = requireNotNull(backStacks[route]) {
                "Stack for $route not found while creating decorated entries"
            }
            decoratedEntries[route] = rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = entryDecorators,
                entryProvider = entryProvider
            )
    }

    return decoratedEntries[topLevelRoute].orEmpty()
}
