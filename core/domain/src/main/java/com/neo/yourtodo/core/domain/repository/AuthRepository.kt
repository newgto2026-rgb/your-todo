package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.auth.AuthSession
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authSession: Flow<AuthSession?>
    suspend fun signInWithGoogle(idToken: String): Result<AuthSession>
    suspend fun completeNicknameOnboarding(nickname: String): Result<AuthSession>
    suspend fun signOut()
}
