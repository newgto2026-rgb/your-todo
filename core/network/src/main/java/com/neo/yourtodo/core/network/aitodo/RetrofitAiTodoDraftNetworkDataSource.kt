package com.neo.yourtodo.core.network.aitodo

import javax.inject.Inject

internal class RetrofitAiTodoDraftNetworkDataSource @Inject constructor(
    private val api: AiTodoDraftApi
) : AiTodoDraftNetworkDataSource {
    override suspend fun parseTodoDrafts(
        request: NetworkAiTodoDraftRequest
    ): NetworkAiTodoDraftResponse = api.parseTodoDrafts(request)
}
