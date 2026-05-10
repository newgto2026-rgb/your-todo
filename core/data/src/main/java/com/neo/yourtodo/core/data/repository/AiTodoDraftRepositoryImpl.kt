package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.domain.repository.AiTodoDraftRepository
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.aitodo.AiTodoDraft
import com.neo.yourtodo.core.model.aitodo.AiTodoDraftResult
import com.neo.yourtodo.core.model.aitodo.AiTodoPerson
import com.neo.yourtodo.core.network.aitodo.AiTodoDraftNetworkDataSource
import com.neo.yourtodo.core.network.aitodo.NetworkAiTodoDraft
import com.neo.yourtodo.core.network.aitodo.NetworkAiTodoDraftRequest
import com.neo.yourtodo.core.network.aitodo.NetworkAiTodoPerson
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

class AiTodoDraftRepositoryImpl @Inject constructor(
    private val networkDataSource: AiTodoDraftNetworkDataSource
) : AiTodoDraftRepository {
    override suspend fun parseTodoDrafts(
        text: String,
        now: Instant,
        zoneId: ZoneId,
        people: List<AiTodoPerson>
    ): Result<AiTodoDraftResult> = runCatching {
        val response = networkDataSource.parseTodoDrafts(
            NetworkAiTodoDraftRequest(
                text = text,
                now = now.toString(),
                timezone = zoneId.id,
                locale = Locale.getDefault().toLanguageTag(),
                people = people.map {
                    NetworkAiTodoPerson(
                        id = it.id,
                        name = it.displayName,
                        aliases = it.aliases,
                        isSelf = it.isSelf
                    )
                }
            )
        )
        AiTodoDraftResult(
            items = response.items.map { it.toDomain(people.map(AiTodoPerson::id).toSet()) },
            model = response.model,
            fallbackUsed = response.fallbackUsed
        )
    }

    private fun NetworkAiTodoDraft.toDomain(knownPersonIds: Set<String>): AiTodoDraft {
        val parsedDate = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val normalizedAssigneeId = assigneeId?.takeIf { it in knownPersonIds }
        return AiTodoDraft(
            title = title.trim(),
            assigneeId = normalizedAssigneeId,
            dueDate = parsedDate,
            dueTimeMinutes = dueTimeMinutes?.takeIf { it in 0..1439 },
            priority = priority.toTodoPriority(),
            needsReview = needsReview || title.isBlank() || assigneeId != null && normalizedAssigneeId == null,
            reviewReason = reviewReason
        )
    }

    private fun String?.toTodoPriority(): TodoPriority =
        when (this?.uppercase(Locale.US)) {
            "LOW" -> TodoPriority.LOW
            "HIGH" -> TodoPriority.HIGH
            else -> TodoPriority.MEDIUM
        }
}
