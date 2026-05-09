package com.neo.yourtodo.core.network.assignments

import javax.inject.Inject
import retrofit2.HttpException

internal class RetrofitAssignmentNetworkDataSource @Inject constructor(
    private val api: AssignmentApi
) : AssignmentNetworkDataSource {
    override suspend fun createBundle(
        accessToken: String,
        idempotencyKey: String,
        request: NetworkCreateAssignmentBundleRequest
    ): NetworkAssignmentBundleResponse =
        runAssignmentRequest {
            api.createBundle(accessToken.authorizationHeader(), idempotencyKey, request)
        }

    override suspend fun getReceivedAssignedTodos(
        accessToken: String,
        status: String
    ): NetworkAssignedTodosResponse =
        runAssignmentRequest { api.getReceivedAssignedTodos(accessToken.authorizationHeader(), status) }

    override suspend fun getSentAssignedTodos(
        accessToken: String,
        status: String
    ): NetworkAssignedTodosResponse =
        runAssignmentRequest { api.getSentAssignedTodos(accessToken.authorizationHeader(), status) }

    override suspend fun getFriendSummary(
        accessToken: String,
        friendUserId: String
    ): NetworkFriendAssignmentSummaryResponse =
        runAssignmentRequest { api.getFriendSummary(accessToken.authorizationHeader(), friendUserId) }

    override suspend fun getFriendAssignedTodos(
        accessToken: String,
        friendUserId: String,
        direction: String,
        status: String
    ): NetworkAssignedTodosResponse =
        runAssignmentRequest {
            api.getFriendAssignedTodos(accessToken.authorizationHeader(), friendUserId, direction, status)
        }

    override suspend fun decideItems(
        accessToken: String,
        idempotencyKey: String,
        bundleId: String,
        request: NetworkDecideAssignmentItemsRequest
    ): NetworkAssignmentBundleResponse =
        runAssignmentRequest {
            api.decideItems(accessToken.authorizationHeader(), idempotencyKey, bundleId, request)
        }

    override suspend fun completeAssignedTodo(
        accessToken: String,
        assignedTodoId: String
    ): NetworkAssignedTodoMutationResponse =
        runAssignmentRequest {
            api.completeAssignedTodo(accessToken.authorizationHeader(), assignedTodoId)
        }

    override suspend fun deleteReceivedAssignedTodo(
        accessToken: String,
        idempotencyKey: String,
        assignedTodoId: String
    ): NetworkAssignedTodoMutationResponse =
        runAssignmentRequest {
            api.deleteReceivedAssignedTodo(
                accessToken.authorizationHeader(),
                idempotencyKey,
                assignedTodoId
            )
        }

    override suspend fun cancelAssignedTodo(
        accessToken: String,
        idempotencyKey: String,
        assignedTodoId: String
    ): NetworkAssignedTodoMutationResponse =
        runAssignmentRequest {
            api.cancelAssignedTodo(accessToken.authorizationHeader(), idempotencyKey, assignedTodoId)
        }

    override suspend fun upsertAssignedTodoReminder(
        accessToken: String,
        assignedTodoId: String,
        request: NetworkUpsertAssignedTodoReminderRequest
    ): NetworkAssignedTodoReminderResponse =
        runAssignmentRequest {
            api.upsertAssignedTodoReminder(accessToken.authorizationHeader(), assignedTodoId, request)
        }

    override suspend fun deleteAssignedTodoReminder(
        accessToken: String,
        assignedTodoId: String
    ): NetworkAssignedTodoReminderResponse =
        runAssignmentRequest {
            api.deleteAssignedTodoReminder(accessToken.authorizationHeader(), assignedTodoId)
        }

    private suspend fun <T> runAssignmentRequest(block: suspend () -> T): T =
        try {
            block()
        } catch (throwable: HttpException) {
            if (throwable.code() == 401) {
                throw AssignmentAuthRequiredException()
            }
            throw throwable
        }

    private fun String.authorizationHeader() = "Bearer $this"
}
