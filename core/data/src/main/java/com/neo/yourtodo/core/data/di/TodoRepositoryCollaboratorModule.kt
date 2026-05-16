package com.neo.yourtodo.core.data.di

import androidx.room.withTransaction
import com.neo.yourtodo.core.data.repository.todo.TodoTimeProvider
import com.neo.yourtodo.core.data.repository.todo.TodoTransactionRunner
import com.neo.yourtodo.core.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class TodoSyncPayloadJson

@Module
@InstallIn(SingletonComponent::class)
internal object TodoRepositoryCollaboratorModule {
    @Provides
    @Singleton
    @TodoSyncPayloadJson
    fun provideTodoSyncPayloadJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    internal fun provideTodoTimeProvider(): TodoTimeProvider =
        TodoTimeProvider { System.currentTimeMillis() }

    @Provides
    @Singleton
    internal fun provideTodoTransactionRunner(database: AppDatabase): TodoTransactionRunner =
        object : TodoTransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T =
                database.withTransaction(block)
        }
}
