package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.repository.CalendarPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class CalendarPreferencesRepositoryImpl @Inject constructor(
    private val preferencesDataSource: UserPreferencesDataSource
) : CalendarPreferencesRepository {
    override fun observeMonthExpanded(): Flow<Boolean> =
        preferencesDataSource.calendarMonthExpanded

    override suspend fun setMonthExpanded(isExpanded: Boolean): Result<Unit> =
        runCatching {
            preferencesDataSource.setCalendarMonthExpanded(isExpanded)
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
        }
}
