package com.neo.yourtodo.core.network.aitodo

import retrofit2.http.Body
import retrofit2.http.POST

interface AiTodoDraftApi {
    @POST("ai/todo-drafts")
    suspend fun parseTodoDrafts(
        @Body request: NetworkAiTodoDraftRequest
    ): NetworkAiTodoDraftResponse
}
