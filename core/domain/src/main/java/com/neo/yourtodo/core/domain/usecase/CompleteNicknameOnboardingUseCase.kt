package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AuthRepository
import javax.inject.Inject

class CompleteNicknameOnboardingUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(nickname: String) =
        authRepository.completeNicknameOnboarding(nickname)
}
