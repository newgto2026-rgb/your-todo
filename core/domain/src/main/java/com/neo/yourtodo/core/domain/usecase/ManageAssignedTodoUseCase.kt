package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import javax.inject.Inject

class ManageAssignedTodoUseCase @Inject constructor(
    private val repository: AssignmentRepository
) {
    suspend fun complete(assignedTodoId: String) = repository.completeAssignedTodo(assignedTodoId)

    suspend fun deleteReceived(assignedTodoId: String) =
        repository.deleteReceivedAssignedTodo(assignedTodoId)

    suspend fun cancel(assignedTodoId: String) = repository.cancelAssignedTodo(assignedTodoId)

    suspend fun upsertReminder(
        assignedTodoId: String,
        reminderAt: String,
        enabled: Boolean
    ) = repository.upsertAssignedTodoReminder(assignedTodoId, reminderAt, enabled)

    suspend fun deleteReminder(assignedTodoId: String) =
        repository.deleteAssignedTodoReminder(assignedTodoId)
}
