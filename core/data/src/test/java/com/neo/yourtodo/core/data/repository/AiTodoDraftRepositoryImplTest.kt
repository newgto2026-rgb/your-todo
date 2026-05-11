package com.neo.yourtodo.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.aitodo.AiTodoPerson
import com.neo.yourtodo.core.network.aitodo.AiTodoDraftNetworkDataSource
import com.neo.yourtodo.core.network.aitodo.NetworkAiTodoDraft
import com.neo.yourtodo.core.network.aitodo.NetworkAiTodoDraftRequest
import com.neo.yourtodo.core.network.aitodo.NetworkAiTodoDraftResponse
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiTodoDraftRepositoryImplTest {
    private val people = listOf(
        AiTodoPerson(
            id = "self",
            displayName = "나",
            aliases = listOf("나"),
            isSelf = true
        )
    )

    @Test
    fun `maps valid network draft to domain`() = runTest {
        val repository = AiTodoDraftRepositoryImpl(
            FakeAiTodoDraftNetworkDataSource(
                NetworkAiTodoDraftResponse(
                    items = listOf(
                        NetworkAiTodoDraft(
                            title = " 빨래하기 ",
                            assigneeId = "self",
                            dueDate = "2026-05-11",
                            dueTimeMinutes = 600,
                            priority = "HIGH",
                            needsReview = false,
                            reviewReason = null
                        )
                    ),
                    model = "qwen3:4b-instruct"
                )
            )
        )

        val result = repository.parseTodoDrafts(
            text = "내일 빨래",
            now = Instant.parse("2026-05-10T00:00:00Z"),
            zoneId = ZoneId.of("Asia/Seoul"),
            people = people
        )

        val item = result.getOrThrow().items.single()
        assertThat(item.title).isEqualTo("빨래하기")
        assertThat(item.assigneeId).isEqualTo("self")
        assertThat(item.dueDate.toString()).isEqualTo("2026-05-11")
        assertThat(item.dueTimeMinutes).isEqualTo(600)
        assertThat(item.priority).isEqualTo(TodoPriority.HIGH)
        assertThat(item.needsReview).isFalse()
    }

    @Test
    fun `unknown assignee is nulled and marked for review`() = runTest {
        val repository = AiTodoDraftRepositoryImpl(
            FakeAiTodoDraftNetworkDataSource(
                NetworkAiTodoDraftResponse(
                    items = listOf(
                        NetworkAiTodoDraft(
                            title = "자료 정리",
                            assigneeId = "ghost",
                            dueDate = "bad-date",
                            dueTimeMinutes = 9999,
                            priority = "unknown",
                            needsReview = false,
                            reviewReason = null
                        )
                    )
                )
            )
        )

        val item = repository.parseTodoDrafts(
            text = "누가 자료 정리",
            now = Instant.parse("2026-05-10T00:00:00Z"),
            zoneId = ZoneId.of("Asia/Seoul"),
            people = people
        ).getOrThrow().items.single()

        assertThat(item.assigneeId).isNull()
        assertThat(item.dueDate).isNull()
        assertThat(item.dueTimeMinutes).isNull()
        assertThat(item.priority).isEqualTo(TodoPriority.MEDIUM)
        assertThat(item.needsReview).isTrue()
    }

    private class FakeAiTodoDraftNetworkDataSource(
        private val response: NetworkAiTodoDraftResponse
    ) : AiTodoDraftNetworkDataSource {
        var request: NetworkAiTodoDraftRequest? = null

        override suspend fun parseTodoDrafts(
            request: NetworkAiTodoDraftRequest
        ): NetworkAiTodoDraftResponse {
            this.request = request
            return response
        }
    }
}
