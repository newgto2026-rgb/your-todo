package com.neo.yourtodo.feature.todo.entry.di

import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import com.neo.yourtodo.feature.todo.impl.navigation.TodoFeatureEntryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class TodoFeatureEntryModule {

    @Binds
    @IntoSet
    abstract fun bindTodoFeatureEntry(entry: TodoFeatureEntryImpl): AppFeatureEntry
}
