package com.neo.yourtodo.core.network.personvisibility

interface PersonVisibilityNetworkDataSource {
    suspend fun getVisibilityGrants(accessToken: String): NetworkVisibilityGrantsResponse

    suspend fun createVisibilityGrant(
        accessToken: String,
        idempotencyKey: String,
        request: NetworkCreateVisibilityGrantRequest
    ): NetworkVisibilityGrant

    suspend fun revokeVisibilityGrant(
        accessToken: String,
        idempotencyKey: String,
        grantId: String
    ): NetworkRevokeVisibilityGrantResponse

    suspend fun syncObservedTodos(
        accessToken: String,
        cursor: String?,
        windowStart: String?,
        windowEnd: String?
    ): NetworkObservedTodoSyncResponse
}

class PersonVisibilityAuthRequiredException : RuntimeException()
