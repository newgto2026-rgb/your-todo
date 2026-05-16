package com.neo.yourtodo.core.data.di

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
}
