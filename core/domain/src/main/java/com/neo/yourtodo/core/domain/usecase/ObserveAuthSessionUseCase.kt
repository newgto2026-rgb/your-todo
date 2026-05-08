package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AuthRepository
import javax.inject.Inject

class ObserveAuthSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke() = authRepository.authSession
}
