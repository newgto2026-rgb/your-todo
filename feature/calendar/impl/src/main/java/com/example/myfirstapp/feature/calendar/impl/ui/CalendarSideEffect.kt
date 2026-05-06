package com.example.myfirstapp.feature.calendar.impl.ui

sealed interface CalendarSideEffect {
    data class NavigateToTodoEdit(val todoId: Long) : CalendarSideEffect
    data class NavigateToTodoAdd(val dueDate: java.time.LocalDate) : CalendarSideEffect
}
