package com.neo.yourtodo.app.todo.reminder

import com.neo.yourtodo.core.domain.scheduler.TodoReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TodoReminderModule {

    @Binds
    @Singleton
    abstract fun bindTodoReminderScheduler(impl: WorkManagerTodoReminderScheduler): TodoReminderScheduler
}
