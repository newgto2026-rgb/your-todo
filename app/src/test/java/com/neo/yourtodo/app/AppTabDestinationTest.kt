package com.neo.yourtodo.app

import com.neo.yourtodo.feature.calendar.api.CalendarRoute
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.feature.todo.api.TodoAllRoute
import com.neo.yourtodo.feature.todo.api.TodoCompletedRoute
import com.neo.yourtodo.feature.todo.api.TodoTodayRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppTabDestinationTest {

    @Test
    fun tabs_includeCalendarRoute() {
        assertThat(AppTabDestination.tabs.map { it.route }).contains(
            CalendarRoute
        )
    }

    @Test
    fun fromRoute_returnsExpectedTabs() {
        assertThat(AppTabDestination.fromRoute(TodoAllRoute))
            .isEqualTo(AppTabDestination.ALL)
        assertThat(AppTabDestination.fromRoute(TodoTodayRoute))
            .isEqualTo(AppTabDestination.TODAY)
        assertThat(AppTabDestination.fromRoute(TodoCompletedRoute))
            .isEqualTo(AppTabDestination.COMPLETED)
        assertThat(AppTabDestination.fromRoute(CalendarRoute))
            .isEqualTo(AppTabDestination.CALENDAR)
    }

    @Test
    fun fromTodoFilter_returnsExpectedTodoTabs() {
        assertThat(AppTabDestination.fromTodoFilter(TodoFilter.ALL))
            .isEqualTo(AppTabDestination.ALL)
        assertThat(AppTabDestination.fromTodoFilter(TodoFilter.TODAY))
            .isEqualTo(AppTabDestination.TODAY)
        assertThat(AppTabDestination.fromTodoFilter(TodoFilter.COMPLETED))
            .isEqualTo(AppTabDestination.COMPLETED)
    }

    @Test
    fun tabs_haveUniqueRoutes() {
        val routes = AppTabDestination.tabs.map { it.route }
        assertThat(routes).containsNoDuplicates()
    }
}
