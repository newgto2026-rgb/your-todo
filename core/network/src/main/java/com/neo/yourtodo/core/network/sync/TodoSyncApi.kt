package com.neo.yourtodo.core.network.sync

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface TodoSyncApi {
    @GET("api/sync/todos")
    suspend fun pullTodos(
        @Header("Authorization") authorization: String,
        @Query("cursor") cursor: String?
    ): NetworkTodoSyncPullResponse

    @POST("api/sync/todos")
    suspend fun pushTodos(
        @Header("Authorization") authorization: String,
        @Body request: NetworkTodoSyncPushRequest
    ): NetworkTodoSyncPushResponse
}
