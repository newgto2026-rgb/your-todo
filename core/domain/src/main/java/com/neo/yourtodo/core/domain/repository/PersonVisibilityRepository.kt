package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.personvisibility.ObservedPersonTodos
import com.neo.yourtodo.core.model.personvisibility.PersonVisibilityGrant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface PersonVisibilityRepository {
    fun observeVisibilityGrants(): Flow<List<PersonVisibilityGrant>>

    suspend fun setVisibilityGrant(friendUserId: String): Result<PersonVisibilityGrant>

    suspend fun revokeVisibilityGrant(friendUserId: String): Result<Unit>

    fun observeObservedTodos(): Flow<List<ObservedPersonTodos>>

    suspend fun refreshVisibilityGrants(): Result<List<PersonVisibilityGrant>> =
        Result.success(emptyList())

    suspend fun syncObservedTodos(
        windowStart: LocalDate? = null,
        windowEnd: LocalDate? = null
    ): Result<Unit> = Result.success(Unit)
}
