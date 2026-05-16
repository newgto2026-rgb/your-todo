package com.neo.yourtodo.core.database.di

import android.content.Context
import androidx.room.Room
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.database.AppDatabaseMigrations
import com.neo.yourtodo.core.database.dao.AssignedTodoDao
import com.neo.yourtodo.core.database.dao.CategoryDao
import com.neo.yourtodo.core.database.dao.ReminderDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.dao.TodoOutboxDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "todo.db"
        ).addMigrations(
            AppDatabaseMigrations.MIGRATION_1_2,
            AppDatabaseMigrations.MIGRATION_2_3,
            AppDatabaseMigrations.MIGRATION_3_4,
            AppDatabaseMigrations.MIGRATION_4_5,
            AppDatabaseMigrations.MIGRATION_5_6,
            AppDatabaseMigrations.MIGRATION_6_7,
            AppDatabaseMigrations.MIGRATION_7_8,
            AppDatabaseMigrations.MIGRATION_8_9,
            AppDatabaseMigrations.MIGRATION_9_10,
            AppDatabaseMigrations.MIGRATION_10_11,
            AppDatabaseMigrations.MIGRATION_11_12
        )
            .build()

    @Provides
    fun provideTodoDao(database: AppDatabase): TodoDao = database.todoDao()

    @Provides
    fun provideTodoOutboxDao(database: AppDatabase): TodoOutboxDao = database.todoOutboxDao()

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideReminderDao(database: AppDatabase): ReminderDao = database.reminderDao()

    @Provides
    fun provideAssignedTodoDao(database: AppDatabase): AssignedTodoDao = database.assignedTodoDao()
}
