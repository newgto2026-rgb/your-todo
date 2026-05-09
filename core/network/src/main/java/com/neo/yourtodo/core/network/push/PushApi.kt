package com.neo.yourtodo.core.network.push

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.HTTP
import retrofit2.http.PUT

internal interface PushApi {
    @PUT("api/push-token")
    suspend fun upsertPushToken(
        @Header("Authorization") authorization: String,
        @Body request: NetworkPushTokenRequest
    ): NetworkPushTokenResponse

    @HTTP(method = "DELETE", path = "api/push-token", hasBody = true)
    suspend fun deletePushToken(
        @Header("Authorization") authorization: String,
        @Body request: NetworkDeletePushTokenRequest
    ): NetworkDeletePushTokenResponse
}
