package com.neo.yourtodo.core.network.push

interface PushNetworkDataSource {
    suspend fun upsertPushToken(
        accessToken: String,
        request: NetworkPushTokenRequest
    ): NetworkPushTokenResponse

    suspend fun deletePushToken(
        accessToken: String,
        request: NetworkDeletePushTokenRequest
    ): NetworkDeletePushTokenResponse
}

class PushAuthRequiredException : RuntimeException()
