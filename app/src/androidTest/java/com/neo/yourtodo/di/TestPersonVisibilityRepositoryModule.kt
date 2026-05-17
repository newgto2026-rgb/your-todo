package com.neo.yourtodo.di

import com.neo.yourtodo.core.data.di.PersonVisibilityRepositoryModule
import com.neo.yourtodo.core.database.dao.PersonVisibilityDao
import com.neo.yourtodo.core.database.entity.ObservedTodoEntity
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.repository.PersonVisibilityRepository
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.personvisibility.ObservedPersonTodos
import com.neo.yourtodo.core.model.personvisibility.ObservedTodo
import com.neo.yourtodo.core.model.personvisibility.PersonVisibilityGrant
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.LocalDate
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [PersonVisibilityRepositoryModule::class]
)
object TestPersonVisibilityRepositoryModule {
    @Provides
    @Singleton
    fun providePersonVisibilityRepository(
        userPreferencesDataSource: UserPreferencesDataSource,
        personVisibilityDao: PersonVisibilityDao
    ): PersonVisibilityRepository =
        TestPersonVisibilityRepository(userPreferencesDataSource, personVisibilityDao)
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TestPersonVisibilityRepository(
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val personVisibilityDao: PersonVisibilityDao
) : PersonVisibilityRepository {

    override fun observeVisibilityGrants(): Flow<List<PersonVisibilityGrant>> = flowOf(emptyList())

    override suspend fun setVisibilityGrant(friendUserId: String): Result<PersonVisibilityGrant> =
        Result.failure(UnsupportedOperationException())

    override suspend fun revokeVisibilityGrant(friendUserId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override fun observeObservedTodos(): Flow<List<ObservedPersonTodos>> =
        userPreferencesDataSource.authSession.flatMapLatest { session ->
            val currentUserId = session?.takeUnless { it.onboardingRequired }?.userId
                ?: return@flatMapLatest flowOf(emptyList())
            personVisibilityDao.observeObservedTodos(currentUserId)
        }.map { observedTodos ->
            observedTodos.groupBy { it.ownerUserId }
                .map { (ownerUserId, todos) ->
                    ObservedPersonTodos(
                        ownerUserId = ownerUserId,
                        todos = todos.map { it.toDomain() }
                    )
                }
        }

    override suspend fun refreshVisibilityGrants(): Result<List<PersonVisibilityGrant>> =
        Result.success(emptyList())

    override suspend fun syncObservedTodos(
        windowStart: LocalDate?,
        windowEnd: LocalDate?
    ): Result<Unit> = Result.success(Unit)

    private fun ObservedTodoEntity.toDomain(): ObservedTodo =
        ObservedTodo(
            id = observedTodoId,
            ownerUserId = ownerUserId,
            sourceTodoId = sourceTodoId,
            grantId = grantId,
            ownerNickname = ownerNickname,
            ownerAvatarUrl = ownerAvatarUrl,
            title = title,
            isDone = isDone,
            dueDate = dueDateEpochDay?.let(LocalDate::ofEpochDay),
            dueTimeMinutes = dueTimeMinutes,
            priority = TodoPriority.MEDIUM,
            projectionVersion = projectionVersion
        )
}
