package com.neo.yourtodo.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.AiTodoDraftRepository
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.aitodo.AiTodoDraft
import com.neo.yourtodo.core.model.aitodo.AiTodoDraftResult
import com.neo.yourtodo.core.model.aitodo.AiTodoPerson
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ParseAiTodoDraftsUseCaseTest {
    private val self = AiTodoPerson(
        id = "self",
        displayName = "나",
        aliases = listOf("나", "내", "본인"),
        isSelf = true
    )

    @Test
    fun `trims prompt and forwards request context`() = runTest {
        val repository = RecordingAiTodoDraftRepository()
        val useCase = ParseAiTodoDraftsUseCase(repository)
        val clock = Clock.fixed(Instant.parse("2026-05-10T00:00:00Z"), ZoneId.of("Asia/Seoul"))

        val result = useCase("  내일 빨래  ", listOf(self), clock, ZoneId.of("Asia/Seoul"))

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.text).isEqualTo("내일 빨래")
        assertThat(repository.people).containsExactly(self)
        assertThat(repository.zoneId).isEqualTo(ZoneId.of("Asia/Seoul"))
    }

    @Test
    fun `blank prompt fails before repository call`() = runTest {
        val repository = RecordingAiTodoDraftRepository()
        val useCase = ParseAiTodoDraftsUseCase(repository)

        val result = useCase(" ", listOf(self))

        assertThat(result.isFailure).isTrue()
        assertThat(repository.called).isFalse()
    }

    @Test
    fun `missing self assignee fails before repository call`() = runTest {
        val repository = RecordingAiTodoDraftRepository()
        val useCase = ParseAiTodoDraftsUseCase(repository)

        val result = useCase(
            text = "neo 빨래",
            people = listOf(self.copy(id = "neo", isSelf = false))
        )

        assertThat(result.isFailure).isTrue()
        assertThat(repository.called).isFalse()
    }

    private class RecordingAiTodoDraftRepository : AiTodoDraftRepository {
        var called = false
        var text: String? = null
        var people: List<AiTodoPerson> = emptyList()
        var zoneId: ZoneId? = null

        override suspend fun parseTodoDrafts(
            text: String,
            now: Instant,
            zoneId: ZoneId,
            people: List<AiTodoPerson>
        ): Result<AiTodoDraftResult> {
            called = true
            this.text = text
            this.people = people
            this.zoneId = zoneId
            return Result.success(
                AiTodoDraftResult(
                    items = listOf(
                        AiTodoDraft(
                            title = "빨래하기",
                            assigneeId = "self",
                            dueDate = null,
                            dueTimeMinutes = null,
                            priority = TodoPriority.MEDIUM,
                            needsReview = false,
                            reviewReason = null
                        )
                    ),
                    model = "fake",
                    fallbackUsed = false
                )
            )
        }
    }
}
