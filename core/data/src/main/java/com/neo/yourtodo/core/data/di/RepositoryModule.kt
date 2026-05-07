package com.neo.yourtodo.core.data.di

import com.neo.yourtodo.core.data.repository.ReminderRepositoryImpl
import com.neo.yourtodo.core.data.repository.TodoRepositoryImpl
import com.neo.yourtodo.core.domain.repository.ReminderRepository
import com.neo.yourtodo.core.domain.repository.TodoCategoryRepository
import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.repository.TodoReminderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTodoItemRepository(impl: TodoRepositoryImpl): TodoItemRepository

    @Binds
    @Singleton
    abstract fun bindTodoCategoryRepository(impl: TodoRepositoryImpl): TodoCategoryRepository

    @Binds
    @Singleton
    abstract fun bindTodoFilterRepository(impl: TodoRepositoryImpl): TodoFilterRepository

    @Binds
    @Singleton
    abstract fun bindTodoReminderRepository(impl: TodoRepositoryImpl): TodoReminderRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository
}
