package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import javax.inject.Inject

class ManageDirectAssignmentConsentUseCase @Inject constructor(
    private val repository: AssignmentRepository
) {
    suspend fun request(friendUserId: String) =
        repository.requestDirectAssignmentConsent(friendUserId)

    suspend fun accept(friendUserId: String) =
        repository.acceptDirectAssignmentConsent(friendUserId)

    suspend fun reject(friendUserId: String) =
        repository.rejectDirectAssignmentConsent(friendUserId)

    suspend fun revoke(friendUserId: String) =
        repository.revokeDirectAssignmentConsent(friendUserId)
}
