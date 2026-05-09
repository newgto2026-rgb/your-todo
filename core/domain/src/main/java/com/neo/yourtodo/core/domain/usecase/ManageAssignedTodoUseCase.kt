package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import javax.inject.Inject

class ManageAssignedTodoUseCase @Inject constructor(
    private val repository: AssignmentRepository,
    private val refreshWorkspaceUseCase: RefreshWorkspaceUseCase
) {
    suspend fun complete(assignedTodoId: String) =
        repository.completeAssignedTodo(assignedTodoId)
            .alsoRefreshWorkspaceOnSuccess()

    suspend fun reopen(assignedTodoId: String) =
        repository.reopenAssignedTodo(assignedTodoId)
            .alsoRefreshWorkspaceOnSuccess()

    suspend fun deleteReceived(assignedTodoId: String) =
        repository.deleteReceivedAssignedTodo(assignedTodoId)
            .alsoRefreshWorkspaceOnSuccess()

    suspend fun cancel(assignedTodoId: String) =
        repository.cancelAssignedTodo(assignedTodoId)
            .alsoRefreshWorkspaceOnSuccess()

    suspend fun upsertReminder(
        assignedTodoId: String,
        reminderAt: String,
        enabled: Boolean
    ) = repository.upsertAssignedTodoReminder(assignedTodoId, reminderAt, enabled)

    suspend fun deleteReminder(assignedTodoId: String) =
        repository.deleteAssignedTodoReminder(assignedTodoId)

    private suspend fun <T> Result<T>.alsoRefreshWorkspaceOnSuccess(): Result<T> {
        if (isSuccess) {
            refreshWorkspaceUseCase()
        }
        return this
    }
}
