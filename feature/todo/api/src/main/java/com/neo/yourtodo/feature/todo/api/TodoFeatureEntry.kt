package com.neo.yourtodo.feature.todo.api

import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
data object TodoAllRoute : NavKey

@Serializable
data object TodoTodayRoute : NavKey

@Serializable
data object TodoCompletedRoute : NavKey

@Serializable
data class TodoEditRoute(
    val todoId: Long,
    val editOnly: Boolean = false
) : NavKey

@Serializable
data class TodoAddRoute(
    val dueDate: String? = null,
    val editOnly: Boolean = false
) : NavKey

@Serializable
data class TodoEditorRoute(
    val todoId: Long? = null,
    val assignedTodoId: String? = null,
    val dueDate: String? = null,
    val editOnly: Boolean = true
) : NavKey

interface TodoFeatureEntry : AppFeatureEntry
