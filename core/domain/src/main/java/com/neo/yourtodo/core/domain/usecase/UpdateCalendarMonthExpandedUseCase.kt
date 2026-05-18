package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.CalendarPreferencesRepository
import javax.inject.Inject

class UpdateCalendarMonthExpandedUseCase @Inject constructor(
    private val repository: CalendarPreferencesRepository
) {
    suspend operator fun invoke(isExpanded: Boolean): Result<Unit> =
        repository.setMonthExpanded(isExpanded)
}
