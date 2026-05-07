package com.neo.yourtodo.app.todo.reminder

import com.neo.yourtodo.core.domain.scheduler.TodoReminderScheduler
import com.neo.yourtodo.core.domain.usecase.GetTodoUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateTodoUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodoReminderWorkerEntryPoint {
    fun getTodoUseCase(): GetTodoUseCase
    fun updateTodoUseCase(): UpdateTodoUseCase
    fun scheduler(): TodoReminderScheduler
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodoReminderRescheduleEntryPoint {
    fun scheduler(): TodoReminderScheduler
}
