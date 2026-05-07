package com.neo.yourtodo.app

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.feature.calendar.api.CalendarRoute
import com.neo.yourtodo.feature.todo.api.TodoAllRoute
import com.neo.yourtodo.feature.todo.api.TodoCompletedRoute
import com.neo.yourtodo.feature.todo.api.TodoEditRoute
import com.neo.yourtodo.feature.todo.api.TodoTodayRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppNavigatorTest {

    @Test
    fun goBack_fromTopLevelRoute_returnsToPreviousTopLevelRoute() {
        val state = testNavigationState()
        val navigator = AppNavigator(state)

        navigator.navigate(TodoTodayRoute)
        navigator.navigate(TodoCompletedRoute)

        assertThat(state.topLevelRoute).isEqualTo(TodoCompletedRoute)
        assertThat(navigator.goBack()).isTrue()
        assertThat(state.topLevelRoute).isEqualTo(TodoTodayRoute)
    }

    @Test
    fun goBack_fromChildRoute_popsCurrentTopLevelStack() {
        val state = testNavigationState(startTopLevelRoute = CalendarRoute)
        val navigator = AppNavigator(state)

        navigator.navigate(TodoEditRoute(todoId = 42L, editOnly = true))

        assertThat(state.topLevelRoute).isEqualTo(CalendarRoute)
        assertThat(state.currentRoute).isEqualTo(TodoEditRoute(todoId = 42L, editOnly = true))

        assertThat(navigator.goBack()).isTrue()
        assertThat(state.topLevelRoute).isEqualTo(CalendarRoute)
        assertThat(state.currentRoute).isEqualTo(CalendarRoute)
    }

    @Test
    fun goBack_fromStartRoute_returnsFalse() {
        val state = testNavigationState()
        val navigator = AppNavigator(state)

        assertThat(navigator.goBack()).isFalse()
    }

    @Test
    fun navigateToAnotherTab_preservesSourceTabChildRoutes() {
        val state = testNavigationState(startTopLevelRoute = CalendarRoute)
        val navigator = AppNavigator(state)

        val editRoute = TodoEditRoute(todoId = 99L, editOnly = true)
        navigator.navigate(editRoute)
        assertThat(state.currentRoute).isEqualTo(editRoute)

        navigator.navigate(TodoTodayRoute)
        assertThat(state.topLevelRoute).isEqualTo(TodoTodayRoute)

        navigator.navigate(CalendarRoute)
        assertThat(state.topLevelRoute).isEqualTo(CalendarRoute)
        assertThat(state.currentRoute).isEqualTo(editRoute)
    }

    @Test
    fun navigateToAnotherTab_removesTransientChildRoutes() {
        val state = testNavigationState(startTopLevelRoute = CalendarRoute)
        val navigator = AppNavigator(
            state = state,
            transientRouteTypes = setOf(TodoEditRoute::class)
        )

        val editRoute = TodoEditRoute(todoId = 99L, editOnly = true)
        navigator.navigate(editRoute)
        assertThat(state.currentRoute).isEqualTo(editRoute)

        navigator.navigate(TodoTodayRoute)
        navigator.navigate(CalendarRoute)

        assertThat(state.topLevelRoute).isEqualTo(CalendarRoute)
        assertThat(state.currentRoute).isEqualTo(CalendarRoute)
    }

    @Test
    fun navigateToTransientRoute_usesOverlayStackWithoutMutatingCurrentTabStack() {
        val state = testNavigationState(startTopLevelRoute = CalendarRoute)
        val navigator = AppNavigator(
            state = state,
            transientRouteTypes = setOf(TodoEditRoute::class)
        )

        val editRoute = TodoEditRoute(todoId = 99L, editOnly = true)
        navigator.navigate(editRoute)

        assertThat(state.currentStack).containsExactly(CalendarRoute).inOrder()
        assertThat(state.transientStack).containsExactly(editRoute).inOrder()
        assertThat(state.currentRoute).isEqualTo(editRoute)
    }

    @Test
    fun closeCurrentEntry_fromTransientRoute_popsOnlyOverlayStack() {
        val state = testNavigationState(startTopLevelRoute = CalendarRoute)
        val navigator = AppNavigator(
            state = state,
            transientRouteTypes = setOf(TodoEditRoute::class)
        )

        navigator.navigate(TodoEditRoute(todoId = 42L, editOnly = true))

        assertThat(navigator.closeCurrentEntry()).isTrue()
        assertThat(state.topLevelRoute).isEqualTo(CalendarRoute)
        assertThat(state.currentStack).containsExactly(CalendarRoute).inOrder()
        assertThat(state.transientStack).isEmpty()
        assertThat(state.currentRoute).isEqualTo(CalendarRoute)
    }

    @Test
    fun closeCurrentEntry_fromChildRoute_popsOnlyChildRoute() {
        val state = testNavigationState(startTopLevelRoute = CalendarRoute)
        val navigator = AppNavigator(state)

        navigator.navigate(TodoEditRoute(todoId = 42L, editOnly = true))

        assertThat(navigator.closeCurrentEntry()).isTrue()
        assertThat(state.topLevelRoute).isEqualTo(CalendarRoute)
        assertThat(state.currentRoute).isEqualTo(CalendarRoute)
    }

    @Test
    fun closeCurrentEntry_fromTopLevelRoute_doesNotPopTopLevelStack() {
        val state = testNavigationState()
        val navigator = AppNavigator(state)

        navigator.navigate(CalendarRoute)

        assertThat(navigator.closeCurrentEntry()).isFalse()
        assertThat(state.topLevelRoute).isEqualTo(CalendarRoute)
    }

    @Test
    fun navigateToSelectedTab_clearsCurrentSubStackToRoot() {
        val state = testNavigationState(startTopLevelRoute = CalendarRoute)
        val navigator = AppNavigator(state)

        navigator.navigate(TodoEditRoute(todoId = 99L, editOnly = true))
        assertThat(state.currentRoute).isEqualTo(TodoEditRoute(todoId = 99L, editOnly = true))

        navigator.navigate(CalendarRoute)

        assertThat(state.topLevelRoute).isEqualTo(CalendarRoute)
        assertThat(state.currentRoute).isEqualTo(CalendarRoute)
    }

    private fun testNavigationState(
        startTopLevelRoute: NavKey = TodoAllRoute
    ): AppNavigationState {
        val topLevelRoutes = setOf(TodoAllRoute, TodoTodayRoute, TodoCompletedRoute, CalendarRoute)
        return AppNavigationState(
            startRoute = TodoAllRoute,
            orderedTopLevelRoutes = topLevelRoutes.toList(),
            topLevelRoutes = topLevelRoutes,
            topLevelStack = NavBackStack<NavKey>(TodoAllRoute).apply {
                if (startTopLevelRoute != TodoAllRoute) {
                    add(startTopLevelRoute)
                }
            },
            backStacks = topLevelRoutes.associateWith { route -> NavBackStack(route) },
            transientStack = mutableStateListOf()
        )
    }
}
