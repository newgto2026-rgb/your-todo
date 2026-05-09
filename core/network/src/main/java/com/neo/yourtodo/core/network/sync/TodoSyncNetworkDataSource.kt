package com.neo.yourtodo.core.network.sync

interface TodoSyncNetworkDataSource {
    suspend fun pullTodos(
        accessToken: String,
        cursor: String?
    ): NetworkTodoSyncPullResponse

    suspend fun pushTodos(
        accessToken: String,
        request: NetworkTodoSyncPushRequest
    ): NetworkTodoSyncPushResponse
}
