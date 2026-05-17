package com.neo.yourtodo.di

import android.content.Context
import androidx.room.Room
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.database.dao.AssignedTodoDao
import com.neo.yourtodo.core.database.dao.CategoryDao
import com.neo.yourtodo.core.database.dao.PersonVisibilityDao
import com.neo.yourtodo.core.database.dao.ReminderDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.dao.TodoOutboxDao
import com.neo.yourtodo.core.database.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
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

    @Provides
    fun providePersonVisibilityDao(database: AppDatabase): PersonVisibilityDao = database.personVisibilityDao()
}
