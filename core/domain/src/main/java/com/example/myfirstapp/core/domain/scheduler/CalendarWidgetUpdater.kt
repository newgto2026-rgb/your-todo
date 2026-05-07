package com.example.myfirstapp.core.domain.scheduler

interface CalendarWidgetUpdater {
    suspend fun updateCalendarWidgets(): Result<Unit>
}
