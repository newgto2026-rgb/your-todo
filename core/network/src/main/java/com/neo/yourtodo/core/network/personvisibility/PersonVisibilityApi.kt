package com.neo.yourtodo.core.network.personvisibility

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PersonVisibilityApi {
    @GET("api/person-visibility/grants")
    suspend fun getVisibilityGrants(
        @Header("Authorization") authorization: String
    ): NetworkVisibilityGrantsResponse

    @POST("api/person-visibility/grants")
    suspend fun createVisibilityGrant(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: NetworkCreateVisibilityGrantRequest
    ): NetworkVisibilityGrantMutationResponse

    @DELETE("api/person-visibility/grants/{grantId}")
    suspend fun revokeVisibilityGrant(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("grantId") grantId: String
    ): NetworkVisibilityGrantMutationResponse

    @GET("api/observed-todos/sync")
    suspend fun syncObservedTodos(
        @Header("Authorization") authorization: String,
        @Query("cursor") cursor: String?,
        @Query("windowStart") windowStart: String?,
        @Query("windowEnd") windowEnd: String?
    ): NetworkObservedTodoSyncResponse
}
