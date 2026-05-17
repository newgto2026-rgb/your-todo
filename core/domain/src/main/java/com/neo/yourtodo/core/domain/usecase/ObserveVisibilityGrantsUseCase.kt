package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.PersonVisibilityRepository
import javax.inject.Inject

class ObserveVisibilityGrantsUseCase @Inject constructor(
    private val repository: PersonVisibilityRepository
) {
    operator fun invoke() = repository.observeVisibilityGrants()
}
