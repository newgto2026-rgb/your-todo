package com.neo.yourtodo.feature.calendar.impl.navigation

import com.neo.yourtodo.core.ui.navigation.AppRouteActions
import com.neo.yourtodo.feature.calendar.api.CalendarDateRoute
import com.neo.yourtodo.feature.calendar.api.CalendarFeatureEntry
import com.neo.yourtodo.feature.calendar.api.CalendarRoute
import com.neo.yourtodo.feature.calendar.impl.ui.screen.CalendarRouteScreen
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import javax.inject.Inject

class CalendarFeatureEntryImpl @Inject constructor() : CalendarFeatureEntry {
    override val route: NavKey = CalendarRoute

    override fun register(
        entryProviderScope: EntryProviderScope<NavKey>,
        routeActions: AppRouteActions
    ) {
        entryProviderScope.entry<CalendarRoute> {
            CalendarRouteScreen(
                initialSelectedDate = null,
                onNavigateToTodoEdit = { todoId ->
                    routeActions.openTodoEdit(todoId)
                },
                onNavigateToTodoAdd = { dueDate ->
                    routeActions.openTodoAdd(dueDate.toString())
                }
            )
        }
        entryProviderScope.entry<CalendarDateRoute> { route ->
            CalendarRouteScreen(
                initialSelectedDate = route.selectedDate,
                onNavigateToTodoEdit = { todoId ->
                    routeActions.openTodoEdit(todoId)
                },
                onNavigateToTodoAdd = { dueDate ->
                    routeActions.openTodoAdd(dueDate.toString())
                }
            )
        }
    }
}
