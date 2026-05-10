package com.neo.yourtodo.core.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass

interface AppFeatureEntry {
    val route: NavKey
    val topLevelRoutes: Set<NavKey>
        get() = setOf(route)
    val transientRouteTypes: Set<KClass<out NavKey>>
        get() = emptySet()
    val isStartDestination: Boolean
        get() = false
    fun register(
        entryProviderScope: EntryProviderScope<NavKey>,
        routeActions: AppRouteActions
    )
}

interface AppNavigator {
    fun navigate(route: NavKey)
    fun goBack(): Boolean
    fun setBackBlocked(blocked: Boolean)
}

interface AppRouteActions {
    val workspaceSyncState: StateFlow<WorkspaceSyncUiState>
        get() = MutableStateFlow(WorkspaceSyncUiState())
    val topLevelLaunchRouteState: StateFlow<NavKey?>
        get() = MutableStateFlow(null)

    fun openTodoEdit(todoId: Long)
    fun openAssignedTodoEdit(assignedTodoId: String)
    fun openTodoAdd(dueDate: String)
    fun requestWorkspaceSync() = Unit
    fun closeCurrentEntry()
    fun setBackBlocked(blocked: Boolean)
}

data class WorkspaceSyncUiState(
    val isSyncing: Boolean = false
)
