package com.neo.yourtodo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.neo.yourtodo.app.navigation.AppNavigationIdentityProbe
import com.neo.yourtodo.app.navigation.RetainedSaveableStateHolderNavEntryDecorator
import com.neo.yourtodo.app.navigation.RetainedViewModelStoreNavEntryDecorator
import com.neo.yourtodo.app.navigation.rememberRetainedSaveableStateHolderNavEntryDecorator
import com.neo.yourtodo.app.navigation.rememberRetainedViewModelStoreNavEntryDecorator

@Composable
fun rememberAppNavigationState(
    startRoute: NavKey,
    topLevelRoutes: List<NavKey>,
    initialTopLevelContentRoute: NavKey? = null
): AppNavigationState {
    val topLevelStack = rememberNavBackStack(startRoute)
    val backStacks = linkedMapOf<NavKey, NavBackStack<NavKey>>()
    val entryStates = linkedMapOf<NavKey, AppNavEntryStackState>()
    topLevelRoutes.forEach { route ->
        key(route) {
            backStacks[route] = if (route == startRoute && initialTopLevelContentRoute != null) {
                rememberNavBackStack(route, initialTopLevelContentRoute)
            } else {
                rememberNavBackStack(route)
            }
            entryStates[route] = rememberAppNavEntryStackState("topLevel:$route")
        }
    }
    val topLevelRouteSet = remember(topLevelRoutes) { topLevelRoutes.toSet() }
    val transientStack = remember { mutableStateListOf<NavKey>() }
    val transientEntryState = rememberAppNavEntryStackState("transient")

    return remember(startRoute, topLevelRoutes, initialTopLevelContentRoute) {
        AppNavigationState(
            startRoute = startRoute,
            orderedTopLevelRoutes = topLevelRoutes,
            topLevelRoutes = topLevelRouteSet,
            topLevelStack = topLevelStack,
            backStacks = backStacks,
            transientStack = transientStack,
            entryStates = entryStates,
            transientEntryState = transientEntryState,
            initialTopLevelContentRoute = initialTopLevelContentRoute
        )
    }
}

class AppNavEntryStackState(
    val entryCache: LinkedHashMap<NavKey, NavEntry<NavKey>>,
    private val saveableStateDecorator: RetainedSaveableStateHolderNavEntryDecorator<NavKey>,
    private val viewModelStoreDecorator: RetainedViewModelStoreNavEntryDecorator<NavKey>
) {
    private val retainedContentKeys = mutableSetOf<Any>()
    private val decoratedEntryCache = linkedMapOf<Any, NavEntry<NavKey>>()

    fun retainContentKeys(contentKeys: Set<Any>) {
        val removedKeys = retainedContentKeys - contentKeys
        removedKeys.forEach { key ->
            decoratedEntryCache.remove(key)
            saveableStateDecorator.clearKey(key)
            viewModelStoreDecorator.clearKey(key)
        }
        retainedContentKeys.clear()
        retainedContentKeys.addAll(contentKeys)
    }

    fun decoratedEntries(entries: List<NavEntry<NavKey>>): List<NavEntry<NavKey>> {
        val contentKeys = entries.map { it.contentKey }.toSet()
        decoratedEntryCache.keys.retainAll(contentKeys)
        return entries.map { entry ->
            val cached = decoratedEntryCache[entry.contentKey]
            cached ?: NavEntry(navEntry = entry) {
                saveableStateDecorator.Decorate(entry.contentKey) {
                    viewModelStoreDecorator.Decorate(entry.contentKey) {
                        entry.Content()
                    }
                }
            }.also { decoratedEntry ->
                decoratedEntryCache[entry.contentKey] = decoratedEntry
            }
        }
    }
}

@Composable
private fun rememberAppNavEntryStackState(scopeName: String): AppNavEntryStackState {
    val entryCache = remember { linkedMapOf<NavKey, NavEntry<NavKey>>() }
    val saveableStateDecorator = rememberRetainedSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStoreDecorator = rememberRetainedViewModelStoreNavEntryDecorator<NavKey>()
    return remember(scopeName, entryCache, saveableStateDecorator, viewModelStoreDecorator) {
        AppNavEntryStackState(
            entryCache = entryCache,
            saveableStateDecorator = saveableStateDecorator,
            viewModelStoreDecorator = viewModelStoreDecorator
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
    val transientEntryState: AppNavEntryStackState? = null,
    val initialTopLevelContentRoute: NavKey? = null
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

    fun isAtInitialTopLevelContentRoot(): Boolean {
        val initialContentRoute = initialTopLevelContentRoute ?: return false
        val stack = currentStack
        return topLevelRoute == startRoute &&
            stack.size == 2 &&
            stack.first() == topLevelRoute &&
            stack.last() == initialContentRoute
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
    SideEffect {
        decoratedTopLevelEntries.forEach { (route, entries) ->
            entryStates.getValue(route).retainContentKeys(entries.map { it.contentKey }.toSet())
        }
        transientEntryState.retainContentKeys(transientEntries.map { it.contentKey }.toSet())
        AppNavigationIdentityProbe.publishEntries(topLevelEntriesInUse + transientEntries)
    }
    return topLevelEntriesInUse + transientEntries
}

@Composable
private fun rememberTopLevelEntries(
    backStack: NavBackStack<NavKey>,
    entryState: AppNavEntryStackState,
    entryProvider: (NavKey) -> NavEntry<NavKey>
): List<NavEntry<NavKey>> {
    val entries = rememberStableNavEntries(
        entryState = entryState,
        backStack = backStack,
        entryProvider = entryProvider
    )
    return entryState.decoratedEntries(entries)
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
    return entryState.decoratedEntries(entries)
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
