package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.ReminderRepository
import com.neo.yourtodo.core.model.Reminder
import javax.inject.Inject

class GetReminderUseCase @Inject constructor(
    private val repository: ReminderRepository
) {
    suspend operator fun invoke(id: Long): Reminder? = repository.getReminder(id)
}
