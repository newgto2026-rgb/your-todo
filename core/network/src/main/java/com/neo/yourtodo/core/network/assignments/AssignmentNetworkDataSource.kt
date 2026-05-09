package com.neo.yourtodo.core.network.assignments

interface AssignmentNetworkDataSource {
    suspend fun createBundle(
        accessToken: String,
        idempotencyKey: String,
        request: NetworkCreateAssignmentBundleRequest
    ): NetworkAssignmentBundleResponse

    suspend fun getReceivedAssignedTodos(
        accessToken: String,
        status: String
    ): NetworkAssignedTodosResponse

    suspend fun getSentAssignedTodos(
        accessToken: String,
        status: String
    ): NetworkAssignedTodosResponse

    suspend fun getFriendSummary(
        accessToken: String,
        friendUserId: String
    ): NetworkFriendAssignmentSummaryResponse

    suspend fun getFriendAssignedTodos(
        accessToken: String,
        friendUserId: String,
        direction: String,
        status: String
    ): NetworkAssignedTodosResponse

    suspend fun decideItems(
        accessToken: String,
        idempotencyKey: String,
        bundleId: String,
        request: NetworkDecideAssignmentItemsRequest
    ): NetworkAssignmentBundleResponse

    suspend fun completeAssignedTodo(accessToken: String, assignedTodoId: String): NetworkAssignedTodoMutationResponse

    suspend fun reopenAssignedTodo(accessToken: String, assignedTodoId: String): NetworkAssignedTodoMutationResponse

    suspend fun deleteReceivedAssignedTodo(
        accessToken: String,
        idempotencyKey: String,
        assignedTodoId: String
    ): NetworkAssignedTodoMutationResponse

    suspend fun cancelAssignedTodo(
        accessToken: String,
        idempotencyKey: String,
        assignedTodoId: String
    ): NetworkAssignedTodoMutationResponse

    suspend fun upsertAssignedTodoReminder(
        accessToken: String,
        assignedTodoId: String,
        request: NetworkUpsertAssignedTodoReminderRequest
    ): NetworkAssignedTodoReminderResponse

    suspend fun deleteAssignedTodoReminder(
        accessToken: String,
        assignedTodoId: String
    ): NetworkAssignedTodoReminderResponse
}

class AssignmentAuthRequiredException : RuntimeException()
