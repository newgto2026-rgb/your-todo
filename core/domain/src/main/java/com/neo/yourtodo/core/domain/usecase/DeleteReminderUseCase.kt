package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.ReminderRepository
import javax.inject.Inject

class DeleteReminderUseCase @Inject constructor(
    private val repository: ReminderRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> = repository.deleteReminder(id)
}
