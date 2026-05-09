package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import javax.inject.Inject

class GetFriendAssignmentSummaryUseCase @Inject constructor(
    private val repository: AssignmentRepository
) {
    suspend operator fun invoke(friendUserId: String) = repository.getFriendSummary(friendUserId)
}
