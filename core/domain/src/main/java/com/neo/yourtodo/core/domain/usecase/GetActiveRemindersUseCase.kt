package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.ReminderRepository
import com.neo.yourtodo.core.model.Reminder
import javax.inject.Inject

class GetActiveRemindersUseCase @Inject constructor(
    private val repository: ReminderRepository
) {
    suspend operator fun invoke(): List<Reminder> = repository.getActiveReminders()
}
