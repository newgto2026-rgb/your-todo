package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import javax.inject.Inject

class SyncTodosUseCase @Inject constructor(
    private val repository: TodoItemRepository,
    private val calendarWidgetUpdater: CalendarWidgetUpdater = NoOpCalendarWidgetUpdater
) {
    suspend operator fun invoke(): Result<Unit> {
        val result = repository.syncTodos()
        if (result.isSuccess) {
            runCatching { calendarWidgetUpdater.updateCalendarWidgets() }
        }
        return result
    }
}

private object NoOpCalendarWidgetUpdater : CalendarWidgetUpdater {
    override suspend fun updateCalendarWidgets(): Result<Unit> = Result.success(Unit)
}
