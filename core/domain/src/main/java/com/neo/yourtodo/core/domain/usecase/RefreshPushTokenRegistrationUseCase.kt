package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.PushTokenRepository
import javax.inject.Inject

class RefreshPushTokenRegistrationUseCase @Inject constructor(
    private val pushTokenRepository: PushTokenRepository
) {
    suspend operator fun invoke(): Result<Unit> = pushTokenRepository.registerCurrentToken()
}
