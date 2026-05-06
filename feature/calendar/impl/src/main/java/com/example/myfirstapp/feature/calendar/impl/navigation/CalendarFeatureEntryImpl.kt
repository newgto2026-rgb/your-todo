package com.example.myfirstapp.feature.calendar.impl.navigation

import com.example.myfirstapp.core.ui.navigation.AppRouteActions
import com.example.myfirstapp.feature.calendar.api.CalendarFeatureEntry
import com.example.myfirstapp.feature.calendar.api.CalendarRoute
import com.example.myfirstapp.feature.calendar.impl.ui.screen.CalendarRouteScreen
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
