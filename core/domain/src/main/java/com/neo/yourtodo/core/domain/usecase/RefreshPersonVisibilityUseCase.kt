package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.PersonVisibilityRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class RefreshPersonVisibilityUseCase @Inject constructor(
    private val repository: PersonVisibilityRepository
) {
    suspend operator fun invoke(
        windowStart: LocalDate? = null,
        windowEnd: LocalDate? = null
    ): Result<Unit> = coroutineScope {
        val grants = async { repository.refreshVisibilityGrants().map { Unit } }
        val observedTodos = async {
            repository.syncObservedTodos(
                windowStart = windowStart,
                windowEnd = windowEnd
            )
        }
        listOf(grants.await(), observedTodos.await())
            .firstOrNull { it.isFailure }
            ?: Result.success(Unit)
    }
}
