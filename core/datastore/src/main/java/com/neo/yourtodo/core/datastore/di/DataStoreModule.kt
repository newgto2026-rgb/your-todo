package com.neo.yourtodo.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.neo.yourtodo.core.datastore.source.AndroidKeyStoreAuthTokenCipher
import com.neo.yourtodo.core.datastore.source.AuthTokenCipher
import com.neo.yourtodo.core.datastore.source.AuthTokenStoragePolicy
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSourceImpl
import com.neo.yourtodo.core.datastore.source.UserPreferencesMigrations
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    @Binds
    abstract fun bindUserPreferencesDataSource(
        impl: UserPreferencesDataSourceImpl
    ): UserPreferencesDataSource

    @Binds
    @Singleton
    abstract fun bindAuthTokenCipher(
        impl: AndroidKeyStoreAuthTokenCipher
    ): AuthTokenCipher

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(
            @ApplicationContext context: Context,
            authTokenStoragePolicy: AuthTokenStoragePolicy
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(
                    UserPreferencesMigrations.encryptLegacyAuthTokens(authTokenStoragePolicy),
                    UserPreferencesMigrations.resetLegacyTodoPriorityFilter
                )
            ) {
                context.preferencesDataStoreFile("user_preferences.pb")
            }
    }
}
