package com.example.myfirstapp.feature.todo.impl.navigation

import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.ui.navigation.AppRouteActions
import com.example.myfirstapp.core.ui.navigation.BottomSheetRouteMetadata
import com.example.myfirstapp.feature.todo.api.TodoAddRoute
import com.example.myfirstapp.feature.todo.api.TodoAllRoute
import com.example.myfirstapp.feature.todo.api.TodoCompletedRoute
import com.example.myfirstapp.feature.todo.api.TodoEditorRoute
import com.example.myfirstapp.feature.todo.api.TodoEditRoute
import com.example.myfirstapp.feature.todo.api.TodoFeatureEntry
import com.example.myfirstapp.feature.todo.api.TodoTodayRoute
import com.example.myfirstapp.feature.todo.impl.ui.editor.TodoEditorRouteScreen
import com.example.myfirstapp.feature.todo.impl.ui.TodoListRoute
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import javax.inject.Inject

class TodoFeatureEntryImpl @Inject constructor() : TodoFeatureEntry {
    override val route: NavKey = TodoAllRoute
    override val topLevelRoutes: Set<NavKey> = setOf(
        TodoAllRoute,
        TodoTodayRoute,
        TodoCompletedRoute
    )
    override val transientRouteTypes = setOf(
        TodoEditorRoute::class,
        TodoEditRoute::class,
        TodoAddRoute::class
    )
    override val isStartDestination: Boolean = true

    override fun register(
        entryProviderScope: EntryProviderScope<NavKey>,
        routeActions: AppRouteActions
    ) {
        entryProviderScope.entry<TodoAllRoute> {
            TodoListRoute(
                presetFilter = TodoFilter.ALL,
                viewModelKey = "todo:all",
                onBackBlockedChange = routeActions::setBackBlocked,
                onAddRequested = { dueDate ->
                    routeActions.openTodoAdd(dueDate?.toString().orEmpty())
                },
                onEditRequested = routeActions::openTodoEdit
            )
        }
        entryProviderScope.entry<TodoTodayRoute> {
            TodoListRoute(
                presetFilter = TodoFilter.TODAY,
                viewModelKey = "todo:today",
                onBackBlockedChange = routeActions::setBackBlocked,
                onAddRequested = { dueDate ->
                    routeActions.openTodoAdd(dueDate?.toString().orEmpty())
                },
                onEditRequested = routeActions::openTodoEdit
            )
        }
        entryProviderScope.entry<TodoCompletedRoute> {
            TodoListRoute(
                presetFilter = TodoFilter.COMPLETED,
                viewModelKey = "todo:completed",
                onBackBlockedChange = routeActions::setBackBlocked,
                onAddRequested = { dueDate ->
                    routeActions.openTodoAdd(dueDate?.toString().orEmpty())
                },
                onEditRequested = routeActions::openTodoEdit
            )
        }
        entryProviderScope.entry<TodoEditorRoute>(
            metadata = BottomSheetRouteMetadata.bottomSheet()
        ) { route ->
            TodoEditorRouteScreen(
                initialTodoId = route.todoId,
                initialDueDate = route.dueDate,
                onExit = routeActions::closeCurrentEntry
            )
        }
        entryProviderScope.entry<TodoEditRoute>(
            metadata = BottomSheetRouteMetadata.bottomSheet()
        ) { route ->
            TodoEditorRouteScreen(
                initialTodoId = route.todoId,
                initialDueDate = null,
                onExit = routeActions::closeCurrentEntry
            )
        }
        entryProviderScope.entry<TodoAddRoute>(
            metadata = BottomSheetRouteMetadata.bottomSheet()
        ) { route ->
            TodoEditorRouteScreen(
                initialTodoId = null,
                initialDueDate = route.dueDate,
                onExit = routeActions::closeCurrentEntry
            )
        }
    }
}
