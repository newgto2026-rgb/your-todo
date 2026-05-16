package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AuthRepository
import com.neo.yourtodo.core.domain.repository.PushTokenRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val pushTokenRepository: PushTokenRepository
) {
    constructor(authRepository: AuthRepository) : this(authRepository, NoopPushTokenRepository)

    suspend operator fun invoke() {
        pushTokenRepository.deleteRegisteredToken()
        authRepository.deleteUserScopedLocalData()
        authRepository.clearAuthSession()
    }
}

private object NoopPushTokenRepository : PushTokenRepository {
    override suspend fun saveCurrentToken(token: String): Result<Unit> = Result.success(Unit)
    override suspend fun registerCurrentToken(): Result<Unit> = Result.success(Unit)
    override suspend fun deleteRegisteredToken(): Result<Unit> = Result.success(Unit)
}
