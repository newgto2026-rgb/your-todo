package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TodoItemRepository {
    fun observeTodos(): Flow<List<TodoItem>>
    fun observeTodosByDueDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TodoItem>>
    suspend fun getTodo(id: Long): TodoItem?

    suspend fun addTodo(
        title: String,
        dueDate: LocalDate?,
        categoryId: Long?,
        dueTimeMinutes: Int? = null,
        reminderAtEpochMillis: Long? = null,
        isReminderEnabled: Boolean = false,
        reminderRepeatType: ReminderRepeatType = ReminderRepeatType.NONE,
        reminderRepeatDaysMask: Int = 0,
        reminderLeadMinutes: Int? = null,
        priority: TodoPriority = TodoPriority.MEDIUM
    ): Result<Long>

    suspend fun updateTodo(
        id: Long,
        title: String,
        dueDate: LocalDate?,
        categoryId: Long?,
        dueTimeMinutes: Int? = null,
        reminderAtEpochMillis: Long? = null,
        isReminderEnabled: Boolean = false,
        reminderRepeatType: ReminderRepeatType = ReminderRepeatType.NONE,
        reminderRepeatDaysMask: Int = 0,
        reminderLeadMinutes: Int? = null,
        priority: TodoPriority = TodoPriority.MEDIUM
    ): Result<Unit>

    suspend fun deleteTodo(id: Long): Result<Unit>
    suspend fun toggleTodoDone(id: Long): Result<Unit>
}
