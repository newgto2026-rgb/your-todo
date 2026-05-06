package com.example.myfirstapp.feature.calendar.impl.ui

import java.time.LocalDate

sealed interface CalendarAction {
    data object OnPreviousMonthClick : CalendarAction
    data object OnNextMonthClick : CalendarAction
    data class OnDateClick(val date: LocalDate) : CalendarAction
    data class OnTodoClick(val todoId: Long) : CalendarAction
    data object OnAddTodoClick : CalendarAction
}
