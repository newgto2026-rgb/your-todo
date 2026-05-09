package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import javax.inject.Inject

class CreateAssignmentBundleUseCase @Inject constructor(
    private val repository: AssignmentRepository
) {
    suspend operator fun invoke(
        receiverUserId: String,
        items: List<AssignmentDraftItem>
    ) = repository.createBundle(receiverUserId, items)
}
