package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AiTodoDraftRepository
import com.neo.yourtodo.core.model.aitodo.AiTodoDraftResult
import com.neo.yourtodo.core.model.aitodo.AiTodoPerson
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

class ParseAiTodoDraftsUseCase @Inject constructor(
    private val repository: AiTodoDraftRepository
) {
    suspend operator fun invoke(
        text: String,
        people: List<AiTodoPerson>,
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<AiTodoDraftResult> {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) {
            return Result.failure(IllegalArgumentException("Prompt must not be blank"))
        }
        if (people.none { it.isSelf }) {
            return Result.failure(IllegalArgumentException("Self assignee is required"))
        }
        return repository.parseTodoDrafts(
            text = normalizedText,
            now = clock.instant(),
            zoneId = zoneId,
            people = people
        )
    }
}
