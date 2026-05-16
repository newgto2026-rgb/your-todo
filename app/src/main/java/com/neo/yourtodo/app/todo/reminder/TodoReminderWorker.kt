package com.neo.yourtodo.app.todo.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.neo.yourtodo.core.domain.reminder.ReminderRecurrenceCalculator
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
import dagger.hilt.android.EntryPointAccessors
import java.time.ZoneId

class TodoReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val todoId = inputData.getLong(TodoReminderConstants.EXTRA_TODO_ID, -1L)
        if (todoId <= 0L) return Result.success()

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            TodoReminderWorkerEntryPoint::class.java
        )

        val getTodo = entryPoint.getTodoUseCase()
        val updateTodo = entryPoint.updateTodoUseCase()
        val scheduler = entryPoint.scheduler()

        val todo = getTodo(todoId) ?: return Result.success()
        if (!todo.isReminderEnabled || todo.reminderAtEpochMillis == null) {
            scheduler.cancel(todo.id)
            return Result.success()
        }

        TodoReminderNotificationHelper.showReminder(applicationContext, todo)

        val nextReminderAt = computeNextReminderAt(todo)

        val updateResult = updateTodo(
            id = todo.id,
            title = todo.title,
            dueDate = todo.dueDate,
            dueTimeMinutes = todo.dueTimeMinutes,
            categoryId = todo.categoryId,
            reminderAtEpochMillis = nextReminderAt,
            isReminderEnabled = nextReminderAt != null,
            reminderRepeatType = if (nextReminderAt != null) todo.reminderRepeatType else ReminderRepeatType.NONE,
            reminderRepeatDaysMask = if (nextReminderAt != null) todo.reminderRepeatDaysMask else 0,
            reminderLeadMinutes = if (nextReminderAt != null) todo.reminderLeadMinutes else null
        )

        if (updateResult.isFailure) return Result.retry()
        val refreshed = getTodo(todoId)
        if (refreshed != null && refreshed.isReminderEnabled && refreshed.reminderAtEpochMillis != null) {
            scheduler.schedule(refreshed)
        } else {
            scheduler.cancel(todo.id)
        }
        return Result.success()
    }

    private fun computeNextReminderAt(todo: TodoItem): Long? {
        val current = todo.reminderAtEpochMillis ?: return null
        return ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = todo.reminderRepeatType,
            repeatDaysMask = todo.reminderRepeatDaysMask,
            nowEpochMillis = System.currentTimeMillis(),
            zoneId = ZoneId.systemDefault()
        )
    }

    companion object {
        fun inputData(todoId: Long) = workDataOf(TodoReminderConstants.EXTRA_TODO_ID to todoId)
    }
}
