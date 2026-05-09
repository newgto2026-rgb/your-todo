package com.neo.yourtodo.core.network.sync

import javax.inject.Inject
import retrofit2.HttpException

internal class RetrofitTodoSyncNetworkDataSource @Inject constructor(
    private val api: TodoSyncApi
) : TodoSyncNetworkDataSource {
    override suspend fun pullTodos(
        accessToken: String,
        cursor: String?
    ): NetworkTodoSyncPullResponse =
        runSyncRequest {
            api.pullTodos(
                authorization = "Bearer $accessToken",
                cursor = cursor
            )
        }

    override suspend fun pushTodos(
        accessToken: String,
        request: NetworkTodoSyncPushRequest
    ): NetworkTodoSyncPushResponse =
        runSyncRequest {
            api.pushTodos(
                authorization = "Bearer $accessToken",
                request = request
            )
        }

    private suspend fun <T> runSyncRequest(block: suspend () -> T): T =
        try {
            block()
        } catch (throwable: HttpException) {
            if (throwable.code() == 401) {
                throw TodoSyncAuthRequiredException()
            }
            throw throwable
        }
}
