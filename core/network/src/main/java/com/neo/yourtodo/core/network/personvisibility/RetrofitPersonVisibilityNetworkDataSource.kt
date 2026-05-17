package com.neo.yourtodo.core.network.personvisibility

import javax.inject.Inject
import retrofit2.HttpException

internal class RetrofitPersonVisibilityNetworkDataSource @Inject constructor(
    private val api: PersonVisibilityApi
) : PersonVisibilityNetworkDataSource {
    override suspend fun getVisibilityGrants(accessToken: String): NetworkVisibilityGrantsResponse =
        runPersonVisibilityRequest {
            api.getVisibilityGrants(accessToken.authorizationHeader())
        }

    override suspend fun createVisibilityGrant(
        accessToken: String,
        idempotencyKey: String,
        request: NetworkCreateVisibilityGrantRequest
    ): NetworkVisibilityGrant =
        runPersonVisibilityRequest {
            api.createVisibilityGrant(accessToken.authorizationHeader(), idempotencyKey, request)
        }.grant

    override suspend fun revokeVisibilityGrant(
        accessToken: String,
        idempotencyKey: String,
        grantId: String
    ): NetworkRevokeVisibilityGrantResponse =
        runPersonVisibilityRequest {
            api.revokeVisibilityGrant(accessToken.authorizationHeader(), idempotencyKey, grantId)
        }.grant.toRevokeResponse()

    override suspend fun syncObservedTodos(
        accessToken: String,
        cursor: String?,
        windowStart: String?,
        windowEnd: String?
    ): NetworkObservedTodoSyncResponse =
        runPersonVisibilityRequest {
            api.syncObservedTodos(accessToken.authorizationHeader(), cursor, windowStart, windowEnd)
        }

    private suspend fun <T> runPersonVisibilityRequest(block: suspend () -> T): T =
        try {
            block()
        } catch (throwable: HttpException) {
            if (throwable.code() == 401) {
                throw PersonVisibilityAuthRequiredException()
            }
            throw throwable
        }

    private fun String.authorizationHeader() = "Bearer $this"

    private fun NetworkVisibilityGrant.toRevokeResponse() = NetworkRevokeVisibilityGrantResponse(
        grantId = id,
        status = status,
        version = version,
        revokedAt = revokedAt
    )
}
