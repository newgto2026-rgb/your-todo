package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.PersonVisibilityRepository
import javax.inject.Inject

class SetVisibilityGrantUseCase @Inject constructor(
    private val repository: PersonVisibilityRepository
) {
    suspend operator fun invoke(friendUserId: String) = repository.setVisibilityGrant(friendUserId)
}
