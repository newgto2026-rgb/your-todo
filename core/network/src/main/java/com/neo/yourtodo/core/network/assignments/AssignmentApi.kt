package com.neo.yourtodo.core.network.assignments

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AssignmentApi {
    @POST("api/assignment-bundles")
    suspend fun createBundle(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: NetworkCreateAssignmentBundleRequest
    ): NetworkAssignmentBundleResponse

    @GET("api/assigned-todos/received")
    suspend fun getReceivedAssignedTodos(
        @Header("Authorization") authorization: String,
        @Query("status") status: String
    ): NetworkAssignedTodosResponse

    @GET("api/assigned-todos/sent")
    suspend fun getSentAssignedTodos(
        @Header("Authorization") authorization: String,
        @Query("status") status: String
    ): NetworkAssignedTodosResponse

    @GET("api/friends/{friendUserId}/assignment-summary")
    suspend fun getFriendSummary(
        @Header("Authorization") authorization: String,
        @Path("friendUserId") friendUserId: String
    ): NetworkFriendAssignmentSummaryResponse

    @GET("api/friends/{friendUserId}/assigned-todos")
    suspend fun getFriendAssignedTodos(
        @Header("Authorization") authorization: String,
        @Path("friendUserId") friendUserId: String,
        @Query("direction") direction: String,
        @Query("status") status: String
    ): NetworkAssignedTodosResponse

    @POST("api/assignment-bundles/{bundleId}/decide-items")
    suspend fun decideItems(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("bundleId") bundleId: String,
        @Body request: NetworkDecideAssignmentItemsRequest
    ): NetworkAssignmentBundleResponse

    @POST("api/friends/{friendUserId}/direct-assignment-consent/request")
    suspend fun requestDirectAssignmentConsent(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("friendUserId") friendUserId: String
    ): NetworkDirectAssignmentConsentSummaryResponse

    @POST("api/friends/{friendUserId}/direct-assignment-consent/accept")
    suspend fun acceptDirectAssignmentConsent(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("friendUserId") friendUserId: String
    ): NetworkDirectAssignmentConsentSummaryResponse

    @POST("api/friends/{friendUserId}/direct-assignment-consent/reject")
    suspend fun rejectDirectAssignmentConsent(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("friendUserId") friendUserId: String
    ): NetworkDirectAssignmentConsentSummaryResponse

    @POST("api/friends/{friendUserId}/direct-assignment-consent/revoke")
    suspend fun revokeDirectAssignmentConsent(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("friendUserId") friendUserId: String
    ): NetworkDirectAssignmentConsentSummaryResponse

    @POST("api/assigned-todos/{assignedTodoId}/complete")
    suspend fun completeAssignedTodo(
        @Header("Authorization") authorization: String,
        @Path("assignedTodoId") assignedTodoId: String
    ): NetworkAssignedTodoMutationResponse

    @POST("api/assigned-todos/{assignedTodoId}/reopen")
    suspend fun reopenAssignedTodo(
        @Header("Authorization") authorization: String,
        @Path("assignedTodoId") assignedTodoId: String
    ): NetworkAssignedTodoMutationResponse

    @POST("api/assigned-todos/{assignedTodoId}/delete-received")
    suspend fun deleteReceivedAssignedTodo(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("assignedTodoId") assignedTodoId: String
    ): NetworkAssignedTodoMutationResponse

    @POST("api/assigned-todos/{assignedTodoId}/cancel")
    suspend fun cancelAssignedTodo(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("assignedTodoId") assignedTodoId: String
    ): NetworkAssignedTodoMutationResponse

    @PATCH("api/assigned-todos/{assignedTodoId}/checklist/{checklistItemId}")
    suspend fun updateChecklistItem(
        @Header("Authorization") authorization: String,
        @Path("assignedTodoId") assignedTodoId: String,
        @Path("checklistItemId") checklistItemId: String,
        @Body request: NetworkUpdateChecklistItemRequest
    ): NetworkAssignedTodoMutationResponse

    @PUT("api/assigned-todos/{assignedTodoId}/my-reminder")
    suspend fun upsertAssignedTodoReminder(
        @Header("Authorization") authorization: String,
        @Path("assignedTodoId") assignedTodoId: String,
        @Body request: NetworkUpsertAssignedTodoReminderRequest
    ): NetworkAssignedTodoReminderResponse

    @DELETE("api/assigned-todos/{assignedTodoId}/my-reminder")
    suspend fun deleteAssignedTodoReminder(
        @Header("Authorization") authorization: String,
        @Path("assignedTodoId") assignedTodoId: String
    ): NetworkAssignedTodoReminderResponse
}
