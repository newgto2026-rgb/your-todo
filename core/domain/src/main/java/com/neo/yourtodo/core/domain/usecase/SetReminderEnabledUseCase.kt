package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.ReminderRepository
import javax.inject.Inject

class SetReminderEnabledUseCase @Inject constructor(
    private val repository: ReminderRepository
) {
    suspend operator fun invoke(id: Long, enabled: Boolean): Result<Unit> =
        repository.setReminderEnabled(id, enabled)
}
