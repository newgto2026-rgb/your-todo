package com.example.myfirstapp.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.myfirstapp.core.datastore.source.UserPreferencesDataSource
import com.example.myfirstapp.core.datastore.source.UserPreferencesDataSourceImpl
import com.example.myfirstapp.core.datastore.source.UserPreferencesMigrations
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

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(UserPreferencesMigrations.resetLegacyTodoPriorityFilter)
            ) {
                context.preferencesDataStoreFile("user_preferences.pb")
            }
    }
}
