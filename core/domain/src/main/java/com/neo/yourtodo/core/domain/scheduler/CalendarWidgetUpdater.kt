package com.neo.yourtodo.core.domain.scheduler

interface CalendarWidgetUpdater {
    suspend fun updateCalendarWidgets(): Result<Unit>
}
