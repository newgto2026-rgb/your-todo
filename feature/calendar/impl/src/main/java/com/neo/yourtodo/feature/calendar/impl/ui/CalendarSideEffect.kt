package com.neo.yourtodo.feature.calendar.impl.ui

import androidx.annotation.StringRes

sealed interface CalendarSideEffect {
    data class NavigateToTodoEdit(val todoId: Long) : CalendarSideEffect
    data class NavigateToAssignedTodoEdit(val assignedTodoId: String) : CalendarSideEffect
    data class NavigateToTodoAdd(val dueDate: java.time.LocalDate) : CalendarSideEffect
    data class ShowSnackbar(@StringRes val messageRes: Int) : CalendarSideEffect
}
