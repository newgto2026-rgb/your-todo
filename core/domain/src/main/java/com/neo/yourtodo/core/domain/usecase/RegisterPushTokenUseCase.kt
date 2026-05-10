package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.PushTokenRepository
import javax.inject.Inject

class RegisterPushTokenUseCase @Inject constructor(
    private val pushTokenRepository: PushTokenRepository
) {
    suspend operator fun invoke(token: String): Result<Unit> =
        pushTokenRepository.saveCurrentToken(token)
            .fold(
                onSuccess = { pushTokenRepository.registerCurrentToken() },
                onFailure = { Result.failure(it) }
            )
}
