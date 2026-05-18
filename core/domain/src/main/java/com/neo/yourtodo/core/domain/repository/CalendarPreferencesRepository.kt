package com.neo.yourtodo.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface CalendarPreferencesRepository {
    fun observeMonthExpanded(): Flow<Boolean>
    suspend fun setMonthExpanded(isExpanded: Boolean): Result<Unit>
}
