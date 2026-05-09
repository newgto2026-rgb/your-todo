package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoPriority
import java.time.LocalDate
import javax.inject.Inject

class UpdateTodoUseCase @Inject constructor(
    private val repository: TodoItemRepository
) {
    suspend operator fun invoke(
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
    ): Result<Unit> {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Title must not be blank"))
        }
        if (normalizedTitle.length > MAX_TITLE_LENGTH) {
            return Result.failure(IllegalArgumentException("Title must be $MAX_TITLE_LENGTH characters or less"))
        }
        return repository.updateTodo(
            id = id,
            title = normalizedTitle,
            dueDate = dueDate,
            categoryId = categoryId,
            dueTimeMinutes = dueTimeMinutes,
            reminderAtEpochMillis = reminderAtEpochMillis,
            isReminderEnabled = isReminderEnabled,
            reminderRepeatType = reminderRepeatType,
            reminderRepeatDaysMask = reminderRepeatDaysMask,
            reminderLeadMinutes = reminderLeadMinutes,
            priority = priority
        )
    }

    private companion object {
        private const val MAX_TITLE_LENGTH = 200
    }
}
