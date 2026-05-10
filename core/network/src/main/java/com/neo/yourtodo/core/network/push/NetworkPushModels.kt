package com.neo.yourtodo.core.network.push

import kotlinx.serialization.Serializable

@Serializable
data class NetworkPushTokenRequest(
    val platform: String,
    val token: String,
    val deviceId: String? = null,
    val appVersion: String? = null
)

@Serializable
data class NetworkDeletePushTokenRequest(
    val token: String
)

@Serializable
data class NetworkPushTokenResponse(
    val token: NetworkPushToken
)

@Serializable
data class NetworkPushToken(
    val id: String,
    val platform: String,
    val lastSeenAt: String
)

@Serializable
data class NetworkDeletePushTokenResponse(
    val revokedCount: Int
)
