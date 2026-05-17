package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.PersonVisibilityRepository
import java.time.LocalDate
import javax.inject.Inject

class RefreshPersonVisibilityUseCase @Inject constructor(
    private val repository: PersonVisibilityRepository
) {
    suspend operator fun invoke(
        windowStart: LocalDate? = null,
        windowEnd: LocalDate? = null
    ): Result<Unit> {
        val grantsResult = repository.refreshVisibilityGrants().map { Unit }
        val observedTodosResult = repository.syncObservedTodos(
            windowStart = windowStart,
            windowEnd = windowEnd
        )
        return listOf(grantsResult, observedTodosResult)
            .firstOrNull { it.isFailure }
            ?: Result.success(Unit)
    }
}
