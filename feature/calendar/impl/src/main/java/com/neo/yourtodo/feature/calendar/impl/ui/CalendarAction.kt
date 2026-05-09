package com.neo.yourtodo.feature.calendar.impl.ui

import java.time.LocalDate

sealed interface CalendarAction {
    data object OnPreviousMonthClick : CalendarAction
    data object OnNextMonthClick : CalendarAction
    data class OnDateClick(val date: LocalDate) : CalendarAction
    data class OnTodoClick(val todoId: Long, val assignedTodoId: String? = null) : CalendarAction
    data class OnToggleTodoDone(val todoId: Long, val assignedTodoId: String? = null) : CalendarAction
    data object OnAddTodoClick : CalendarAction
    data object OnSyncClick : CalendarAction
    data object OnScreenStarted : CalendarAction
}
