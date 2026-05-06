package com.example.myfirstapp.app

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.example.myfirstapp.feature.calendar.api.CalendarRoute
import com.example.myfirstapp.feature.todo.api.TodoAllRoute
import com.example.myfirstapp.feature.todo.api.TodoCompletedRoute
import com.example.myfirstapp.feature.todo.api.TodoEditRoute
import com.example.myfirstapp.feature.todo.api.TodoTodayRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppNavigatorTest {

    @Test
    fun goBack_fromTopLevelRoute_doesNotTraverseTabHistory() {
        val state = testNavigationState()
        val navigator = AppNavigator(state)

        navigator.navigate(TodoTodayRoute)
        navigator.navigate(TodoCompletedRoute)

        assertThat(state.topLevelRoute).isEqualTo(TodoCompletedRoute)
        assertThat(navigator.goBack()).isFalse()
        assertThat(state.topLevelRoute).isEqualTo(TodoCompletedRoute)
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

    private fun testNavigationState(
        startTopLevelRoute: NavKey = TodoAllRoute
    ): AppNavigationState {
        val topLevelRoutes = setOf(TodoAllRoute, TodoTodayRoute, TodoCompletedRoute, CalendarRoute)
        return AppNavigationState(
            startRoute = TodoAllRoute,
            topLevelRoutes = topLevelRoutes,
            topLevelRoute = mutableStateOf(startTopLevelRoute),
            topLevelHistory = mutableStateOf(
                if (startTopLevelRoute == TodoAllRoute) {
                    emptyList()
                } else {
                    listOf(TodoAllRoute)
                }
            ),
            backStacks = topLevelRoutes.associateWith { route -> NavBackStack(route) }
        )
    }
}
