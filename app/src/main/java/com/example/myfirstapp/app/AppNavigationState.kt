package com.example.myfirstapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

@Composable
fun rememberAppNavigationState(
    startRoute: NavKey,
    topLevelRoutes: List<NavKey>
): AppNavigationState {
    val topLevelStack = rememberNavBackStack(startRoute)
    val backStacks = linkedMapOf<NavKey, NavBackStack<NavKey>>()
    val entryStates = linkedMapOf<NavKey, AppNavEntryStackState>()
    topLevelRoutes.forEach { route ->
        key(route) {
            backStacks[route] = rememberNavBackStack(route)
            entryStates[route] = rememberAppNavEntryStackState("topLevel:$route")
        }
    }
    val topLevelRouteSet = remember(topLevelRoutes) { topLevelRoutes.toSet() }
    val transientStack = remember { mutableStateListOf<NavKey>() }
    val transientEntryState = rememberAppNavEntryStackState("transient")

    return remember(startRoute, topLevelRoutes) {
        AppNavigationState(
            startRoute = startRoute,
            orderedTopLevelRoutes = topLevelRoutes,
            topLevelRoutes = topLevelRouteSet,
            topLevelStack = topLevelStack,
            backStacks = backStacks,
            transientStack = transientStack,
            entryStates = entryStates,
            transientEntryState = transientEntryState
        )
    }
}

class AppNavEntryStackState(
    val entryCache: LinkedHashMap<NavKey, NavEntry<NavKey>>,
    val decorators: List<NavEntryDecorator<NavKey>>
)

@Composable
private fun rememberAppNavEntryStackState(scopeName: String): AppNavEntryStackState {
    val entryCache = remember { linkedMapOf<NavKey, NavEntry<NavKey>>() }
    val saveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val decorators = remember(saveableStateDecorator, viewModelStoreDecorator) {
        listOf(
            saveableStateDecorator,
            viewModelStoreDecorator
        )
    }
    return remember(scopeName, entryCache, decorators) {
        AppNavEntryStackState(
            entryCache = entryCache,
            decorators = decorators
        )
    }
}

class AppNavigationState(
    val startRoute: NavKey,
    val orderedTopLevelRoutes: List<NavKey>,
    val topLevelRoutes: Set<NavKey>,
    val topLevelStack: NavBackStack<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
    val transientStack: SnapshotStateList<NavKey>,
    val entryStates: Map<NavKey, AppNavEntryStackState> = emptyMap(),
    val transientEntryState: AppNavEntryStackState? = null
) {
    var topLevelRoute: NavKey
        get() = topLevelStack.last()
        set(route) {
            require(route in topLevelRoutes) {
                "Top-level route $route is not registered: $topLevelRoutes"
            }
            topLevelStack.apply {
                if (route == startRoute) {
                    clear()
                } else {
                    remove(route)
                }
                add(route)
            }
        }

    val currentRoute: NavKey
        get() = transientStack.lastOrNull() ?: currentStack.last()

    val currentStack: NavBackStack<NavKey>
        get() = requireNotNull(backStacks[topLevelRoute]) {
            "Stack for $topLevelRoute not found"
        }

}

@Composable
fun AppNavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): List<NavEntry<NavKey>> {
    val decoratedTopLevelEntries = backStacks.mapValues { (route, backStack) ->
        key(route) {
            rememberTopLevelEntries(
                backStack = backStack,
                entryState = entryStates.getValue(route),
                entryProvider = entryProvider
            )
        }
    }

    val transientEntries = rememberTransientEntries(
        backStack = transientStack,
        entryState = checkNotNull(transientEntryState) {
            "Transient entry state is required when rendering navigation entries."
        },
        entryProvider = entryProvider
    )
    val topLevelEntriesInUse = orderedTopLevelRoutes
        .flatMap { route -> decoratedTopLevelEntries.getValue(route) }
    return topLevelEntriesInUse + transientEntries
}

@Composable
private fun rememberTopLevelEntries(
    backStack: NavBackStack<NavKey>,
    entryState: AppNavEntryStackState,
    entryProvider: (NavKey) -> NavEntry<NavKey>
): List<NavEntry<NavKey>> {
    return rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = entryState.decorators,
        entryProvider = entryProvider,
    )
}

@Composable
private fun rememberTransientEntries(
    backStack: List<NavKey>,
    entryState: AppNavEntryStackState,
    entryProvider: (NavKey) -> NavEntry<NavKey>
): List<NavEntry<NavKey>> {
    val entries = rememberStableNavEntries(
        entryState = entryState,
        backStack = backStack,
        entryProvider = entryProvider
    )
    return rememberDecoratedNavEntries(
        entries = entries,
        entryDecorators = entryState.decorators,
    )
}

@Composable
private fun rememberStableNavEntries(
    entryState: AppNavEntryStackState,
    backStack: List<NavKey>,
    entryProvider: (NavKey) -> NavEntry<NavKey>
): List<NavEntry<NavKey>> {
    val stackSnapshot = backStack.toList()
    return remember(stackSnapshot, entryProvider) {
        val retainedRoutes = stackSnapshot.toSet()
        entryState.entryCache.keys.retainAll(retainedRoutes)
        stackSnapshot.map { route ->
            entryState.entryCache.getOrPut(route) { entryProvider(route) }
        }
    }
}
