package com.neo.yourtodo.core.testing.repository

import com.neo.yourtodo.core.domain.repository.PersonVisibilityRepository
import com.neo.yourtodo.core.model.personvisibility.ObservedPersonTodos
import com.neo.yourtodo.core.model.personvisibility.PersonVisibilityGrant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePersonVisibilityRepository : PersonVisibilityRepository {
    private val grants = MutableStateFlow<List<PersonVisibilityGrant>>(emptyList())
    private val observedTodos = MutableStateFlow<List<ObservedPersonTodos>>(emptyList())

    var setVisibilityGrantResult: Result<PersonVisibilityGrant>? = null
    var revokeVisibilityGrantResult: Result<Unit>? = null
    var refreshVisibilityGrantsResult: Result<List<PersonVisibilityGrant>>? = null
    var syncObservedTodosResult: Result<Unit>? = null
    var beforeRefreshVisibilityGrants: (suspend () -> Unit)? = null
    var beforeSyncObservedTodos: (suspend () -> Unit)? = null
    val setVisibilityGrantFriendUserIds = mutableListOf<String>()
    val revokedVisibilityGrantFriendUserIds = mutableListOf<String>()
    var refreshVisibilityGrantCount: Int = 0
        private set
    val syncObservedTodoWindows = mutableListOf<Pair<LocalDate?, LocalDate?>>()

    override fun observeVisibilityGrants(): Flow<List<PersonVisibilityGrant>> = grants.asStateFlow()

    override suspend fun setVisibilityGrant(friendUserId: String): Result<PersonVisibilityGrant> {
        setVisibilityGrantFriendUserIds += friendUserId
        return setVisibilityGrantResult ?: Result.failure(UnsupportedOperationException())
    }

    override suspend fun revokeVisibilityGrant(friendUserId: String): Result<Unit> {
        revokedVisibilityGrantFriendUserIds += friendUserId
        return revokeVisibilityGrantResult ?: Result.failure(UnsupportedOperationException())
    }

    override fun observeObservedTodos(): Flow<List<ObservedPersonTodos>> = observedTodos.asStateFlow()

    override suspend fun refreshVisibilityGrants(): Result<List<PersonVisibilityGrant>> {
        refreshVisibilityGrantCount += 1
        beforeRefreshVisibilityGrants?.invoke()
        return refreshVisibilityGrantsResult ?: Result.success(grants.value)
    }

    override suspend fun syncObservedTodos(
        windowStart: LocalDate?,
        windowEnd: LocalDate?
    ): Result<Unit> {
        syncObservedTodoWindows += windowStart to windowEnd
        beforeSyncObservedTodos?.invoke()
        return syncObservedTodosResult ?: Result.success(Unit)
    }

    fun setVisibilityGrants(value: List<PersonVisibilityGrant>) {
        grants.value = value
    }

    fun setObservedTodos(value: List<ObservedPersonTodos>) {
        observedTodos.value = value
    }
}
