package com.neo.yourtodo.core.network.aitodo

interface AiTodoDraftNetworkDataSource {
    suspend fun parseTodoDrafts(request: NetworkAiTodoDraftRequest): NetworkAiTodoDraftResponse
}
