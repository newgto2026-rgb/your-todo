package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String) = authRepository.signInWithGoogle(idToken)
}
