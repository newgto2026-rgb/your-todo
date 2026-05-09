package com.neo.yourtodo.core.domain.repository

interface PushTokenRepository {
    suspend fun saveCurrentToken(token: String): Result<Unit>
    suspend fun registerCurrentToken(): Result<Unit>
    suspend fun deleteRegisteredToken(): Result<Unit>
}
