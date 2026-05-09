package com.neo.yourtodo.app

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import com.neo.yourtodo.core.ui.navigation.AppRouteActions
import com.neo.yourtodo.feature.calendar.api.CalendarRoute
import com.neo.yourtodo.feature.friends.api.FriendsRoute
import com.neo.yourtodo.feature.todo.api.TodoAllRoute
import com.neo.yourtodo.feature.todo.api.TodoCompletedRoute
import com.neo.yourtodo.feature.todo.api.TodoTodayRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.reflect.KClass

class AppNavigationGraphTest {

    @Test
    fun buildAppNavigationGraph_usesStableTabOrderAndStartRoute() {
        val graph = buildAppNavigationGraph(
            entries = setOf(
                fakeEntry(
                    route = TodoAllRoute,
                    topLevelRoutes = setOf(TodoAllRoute, TodoTodayRoute, TodoCompletedRoute),
                    isStartDestination = true
                ),
                fakeEntry(route = CalendarRoute),
                fakeEntry(route = FriendsRoute)
            )
        )

        assertThat(graph.startRoute).isEqualTo(TodoAllRoute)
        assertThat(graph.topLevelRoutes).containsExactly(
            TodoAllRoute,
            TodoTodayRoute,
            TodoCompletedRoute,
            CalendarRoute,
            FriendsRoute
        ).inOrder()
    }

    @Test
    fun buildAppNavigationGraph_collectsTransientRouteTypes() {
        val graph = buildAppNavigationGraph(
            entries = setOf(
                fakeEntry(
                    route = TodoAllRoute,
                    topLevelRoutes = setOf(TodoAllRoute, TodoTodayRoute, TodoCompletedRoute),
                    transientRouteTypes = setOf(ExternalChildRoute::class),
                    isStartDestination = true
                ),
                fakeEntry(route = CalendarRoute),
                fakeEntry(route = FriendsRoute)
            )
        )

        assertThat(graph.transientRouteTypes).containsExactly(ExternalChildRoute::class)
    }

    @Test
    fun buildAppNavigationGraph_rejectsTabWithoutFeatureOwner() {
        val result = runCatching {
            buildAppNavigationGraph(
                entries = setOf(
                    fakeEntry(
                        route = TodoAllRoute,
                        topLevelRoutes = setOf(TodoAllRoute, TodoTodayRoute, TodoCompletedRoute),
                        isStartDestination = true
                    )
                )
            )
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("Missing owners")
        assertThat(result.exceptionOrNull()?.message).contains(CalendarRoute.toString())
    }

    @Test
    fun buildAppNavigationGraph_rejectsDuplicateTopLevelRouteOwners() {
        val result = runCatching {
            buildAppNavigationGraph(
                entries = setOf(
                    fakeEntry(route = TodoAllRoute),
                    fakeEntry(route = TodoTodayRoute, topLevelRoutes = setOf(TodoAllRoute))
                )
            )
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("owned by multiple feature entries")
    }

    @Test
    fun buildAppNavigationGraph_rejectsStartRouteOutsideTabs() {
        val result = runCatching {
            buildAppNavigationGraph(
                entries = setOf(
                    fakeEntry(
                        route = ExternalStartRoute,
                        topLevelRoutes = AppTabDestination.tabs.map(AppTabDestination::route)
                            .toSet() + ExternalStartRoute,
                        isStartDestination = true
                    )
                )
            )
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("must be one of the top-level tab routes")
    }

    @Test
    fun buildAppNavigationGraph_rejectsTransientTopLevelRoute() {
        val result = runCatching {
            buildAppNavigationGraph(
                entries = setOf(
                    fakeEntry(
                        route = TodoAllRoute,
                        topLevelRoutes = setOf(TodoAllRoute, TodoTodayRoute, TodoCompletedRoute),
                        transientRouteTypes = setOf(TodoAllRoute::class),
                        isStartDestination = true
                    ),
                    fakeEntry(route = CalendarRoute),
                    fakeEntry(route = FriendsRoute)
                )
            )
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("Top-level routes cannot be transient")
    }

    private fun fakeEntry(
        route: NavKey,
        topLevelRoutes: Set<NavKey> = setOf(route),
        transientRouteTypes: Set<KClass<out NavKey>> = emptySet(),
        isStartDestination: Boolean = false
    ): AppFeatureEntry = object : AppFeatureEntry {
        override val route: NavKey = route
        override val topLevelRoutes: Set<NavKey> = topLevelRoutes
        override val transientRouteTypes: Set<KClass<out NavKey>> = transientRouteTypes
        override val isStartDestination: Boolean = isStartDestination

        override fun register(
            entryProviderScope: EntryProviderScope<NavKey>,
            routeActions: AppRouteActions
        ) = Unit
    }

    private data object ExternalStartRoute : NavKey
    private data object ExternalChildRoute : NavKey
}
