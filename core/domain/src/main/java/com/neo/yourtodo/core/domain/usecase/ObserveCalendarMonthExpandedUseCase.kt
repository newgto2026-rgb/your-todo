package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.CalendarPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCalendarMonthExpandedUseCase @Inject constructor(
    private val repository: CalendarPreferencesRepository
) {
    operator fun invoke(): Flow<Boolean> = repository.observeMonthExpanded()
}
