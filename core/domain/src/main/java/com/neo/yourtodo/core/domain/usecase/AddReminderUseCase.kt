package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.ReminderRepository
import com.neo.yourtodo.core.model.ReminderRepeatType
import javax.inject.Inject

class AddReminderUseCase @Inject constructor(
    private val repository: ReminderRepository
) {
    suspend operator fun invoke(
        title: String,
        note: String?,
        triggerAtEpochMillis: Long,
        repeatType: ReminderRepeatType,
        repeatDaysMask: Int,
        isEnabled: Boolean = true
    ): Result<Long> {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Title must not be blank"))
        }

        return repository.addReminder(
            title = normalizedTitle,
            note = note?.trim()?.ifBlank { null },
            triggerAtEpochMillis = triggerAtEpochMillis,
            repeatType = repeatType,
            repeatDaysMask = repeatDaysMask,
            isEnabled = isEnabled
        )
    }
}
