package com.neo.yourtodo.app.todo.reminder

object TodoReminderConstants {
    const val CHANNEL_ID = "todo_reminder"
    const val CHANNEL_NAME = "Todo Reminders"
    const val CHANNEL_DESCRIPTION = "Todo item reminders"

    const val EXTRA_TODO_ID = "extra_todo_id"
    const val WORK_PREFIX = "todo_reminder_work_"

    fun workName(todoId: Long): String = "$WORK_PREFIX$todoId"
}
