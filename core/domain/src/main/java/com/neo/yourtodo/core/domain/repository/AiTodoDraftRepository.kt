package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.aitodo.AiTodoDraftResult
import com.neo.yourtodo.core.model.aitodo.AiTodoPerson
import java.time.Instant
import java.time.ZoneId

interface AiTodoDraftRepository {
    suspend fun parseTodoDrafts(
        text: String,
        now: Instant,
        zoneId: ZoneId,
        people: List<AiTodoPerson>
    ): Result<AiTodoDraftResult>
}
