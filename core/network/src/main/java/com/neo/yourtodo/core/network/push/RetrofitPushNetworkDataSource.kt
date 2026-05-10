package com.neo.yourtodo.core.network.push

import javax.inject.Inject
import retrofit2.HttpException

internal class RetrofitPushNetworkDataSource @Inject constructor(
    private val api: PushApi
) : PushNetworkDataSource {
    override suspend fun upsertPushToken(
        accessToken: String,
        request: NetworkPushTokenRequest
    ): NetworkPushTokenResponse =
        runPushRequest {
            api.upsertPushToken(accessToken.authorizationHeader(), request)
        }

    override suspend fun deletePushToken(
        accessToken: String,
        request: NetworkDeletePushTokenRequest
    ): NetworkDeletePushTokenResponse =
        runPushRequest {
            api.deletePushToken(accessToken.authorizationHeader(), request)
        }

    private suspend fun <T> runPushRequest(block: suspend () -> T): T =
        try {
            block()
        } catch (throwable: HttpException) {
            if (throwable.code() == 401) {
                throw PushAuthRequiredException()
            }
            throw throwable
        }

    private fun String.authorizationHeader(): String = "Bearer $this"
}
