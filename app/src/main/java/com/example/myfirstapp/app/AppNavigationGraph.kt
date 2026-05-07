package com.example.myfirstapp.app

import androidx.navigation3.runtime.NavKey
import com.example.myfirstapp.core.ui.navigation.AppFeatureEntry
import kotlin.reflect.KClass

internal data class AppNavigationGraph(
    val featureEntries: List<AppFeatureEntry>,
    val topLevelRoutes: List<NavKey>,
    val startRoute: NavKey,
    val transientRouteTypes: Set<KClass<out NavKey>>
)

internal fun buildAppNavigationGraph(
    entries: Set<AppFeatureEntry>,
    tabs: List<AppTabDestination> = AppTabDestination.tabs
): AppNavigationGraph {
    val featureEntries = entries.sortedBy { entry -> entry.route.toString() }
    val tabRoutes = tabs.map(AppTabDestination::route)
    require(tabRoutes.distinct().size == tabRoutes.size) {
        "Duplicate top-level tab routes are not allowed: ${tabRoutes.duplicates()}"
    }

    val ownerByTopLevelRoute = linkedMapOf<NavKey, AppFeatureEntry>()
    featureEntries.forEach { entry ->
        entry.topLevelRoutes.forEach { route ->
            val previousOwner = ownerByTopLevelRoute.put(route, entry)
            require(previousOwner == null) {
                "Top-level route $route is owned by multiple feature entries: " +
                    "${previousOwner?.javaClass?.name}, ${entry.javaClass.name}"
            }
        }
    }

    val missingRoutes = tabRoutes.filterNot(ownerByTopLevelRoute::containsKey)
    require(missingRoutes.isEmpty()) {
        "Every app tab must be declared by a feature entry. Missing owners for: $missingRoutes"
    }
    val transientRouteTypes = featureEntries
        .flatMap { entry -> entry.transientRouteTypes }
        .toSet()
    val topLevelRouteTypes = ownerByTopLevelRoute.keys
        .map { route -> route::class }
        .toSet()
    val transientTopLevelRouteTypes = transientRouteTypes.intersect(topLevelRouteTypes)
    require(transientTopLevelRouteTypes.isEmpty()) {
        "Top-level routes cannot be transient: $transientTopLevelRouteTypes"
    }

    val startEntries = featureEntries.filter(AppFeatureEntry::isStartDestination)
    require(startEntries.size <= 1) {
        "Only one feature entry can be the start destination: " +
            startEntries.map { entry -> entry.javaClass.name }
    }
    val startRoute = startEntries.singleOrNull()?.route ?: AppTabDestination.ALL.route
    require(startRoute in tabRoutes) {
        "Start route $startRoute must be one of the top-level tab routes: $tabRoutes"
    }

    return AppNavigationGraph(
        featureEntries = featureEntries,
        topLevelRoutes = tabRoutes,
        startRoute = startRoute,
        transientRouteTypes = transientRouteTypes
    )
}

private fun <T> List<T>.duplicates(): Set<T> =
    groupingBy { it }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
