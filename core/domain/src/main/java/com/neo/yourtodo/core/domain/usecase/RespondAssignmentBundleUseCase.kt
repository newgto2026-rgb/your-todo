package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import javax.inject.Inject

class RespondAssignmentBundleUseCase @Inject constructor(
    private val repository: AssignmentRepository
) {
    suspend operator fun invoke(
        bundleId: String,
        decisions: Map<String, AssignmentDecision>
    ) = repository.decideBundleItems(bundleId, decisions)
}
