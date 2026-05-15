package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentSummary
import kotlinx.coroutines.flow.Flow

interface AssignmentRepository {
    suspend fun createBundle(
        receiverUserId: String,
        items: List<AssignmentDraftItem>
    ): Result<AssignmentBundle> = createBundle(receiverUserId, items, AssignmentMode.REQUEST)

    suspend fun createBundle(
        receiverUserId: String,
        items: List<AssignmentDraftItem>,
        assignmentMode: AssignmentMode = AssignmentMode.REQUEST
    ): Result<AssignmentBundle>

    suspend fun requestDirectAssignmentConsent(friendUserId: String): Result<DirectAssignmentConsentSummary> =
        Result.failure(UnsupportedOperationException())

    suspend fun acceptDirectAssignmentConsent(friendUserId: String): Result<DirectAssignmentConsentSummary> =
        Result.failure(UnsupportedOperationException())

    suspend fun rejectDirectAssignmentConsent(friendUserId: String): Result<DirectAssignmentConsentSummary> =
        Result.failure(UnsupportedOperationException())

    suspend fun revokeDirectAssignmentConsent(friendUserId: String): Result<DirectAssignmentConsentSummary> =
        Result.failure(UnsupportedOperationException())

    suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary>

    suspend fun getFriendAssignedTodos(
        friendUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus
    ): Result<List<AssignedTodo>>

    suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>>

    suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>>

    fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>>

    fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>>

    fun observeFriendAssignedTodos(
        friendUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus
    ): Flow<List<AssignedTodo>>

    suspend fun decideBundleItems(
        bundleId: String,
        decisions: Map<String, AssignmentDecision>
    ): Result<AssignmentBundle>

    suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo>

    suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo>

    suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo>

    suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit>

    suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo>

    suspend fun upsertAssignedTodoReminder(
        assignedTodoId: String,
        reminderAt: String,
        enabled: Boolean
    ): Result<Unit>

    suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit>
}

enum class AssignmentDirection {
    SENT,
    RECEIVED
}

enum class AssignmentFeedStatus(val wireValue: String) {
    ACTIVE("active"),
    PENDING("pending"),
    HISTORY("history")
}
