package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.ReminderRepository
import com.neo.yourtodo.core.model.Reminder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRemindersUseCase @Inject constructor(
    private val repository: ReminderRepository
) {
    operator fun invoke(): Flow<List<Reminder>> = repository.observeReminders()
}
